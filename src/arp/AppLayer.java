package arp;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.StringTokenizer;

public class AppLayer extends JFrame implements BaseLayer {

	private JPanel contentPane, dialogPane, errorPane;
	private JTextField ipAddressTextField;
	private JTextField gratuitiousArpTextField;
	private JTextField ipAddressDlgTF;
	private JTextField ethernetAddressDlgTF;

	JTable arpCacheTable, proxyArpTable;
	DefaultTableModel arpCacheTableModel, proxyArpTableModel;
	JScrollPane arpCacheScrollPane, proxyArpScrollPane;
	DefaultTableCellRenderer arpTableRenderer = new DefaultTableCellRenderer();
	DefaultTableCellRenderer proxyTableRenderer = new DefaultTableCellRenderer();
	JTableHeader arpTableHeader, proxyTableHeader;
	TableColumnModel arpColumnModel, proxyColumnModel;

	JComboBox comboBoxDevice;

	String arpStatus, arpEthernetAddress;

	final String[] proxyArpTableHeader = { "Device", "IP 주소", "Ethernet 주소" };
	final String[] arpCacheTableHeader = { "Interface", "IP 주소", "Ethernet 주소", "Status" };
	String[][] proxyArpTableContents = new String[0][3];
	String[][] arpCacheTableContents = new String[0][4];



	// field for layer
	static TCPLayer tcpLayer;
	static IPLayer ipLayer;
	static ARPLayer arpLayer;
	static EthernetLayer ethernetLayer;
	static NILayer niLayer;

	public String pLayerName = null;
	public int nUpperLayerCount = 0;
	public BaseLayer p_UnderLayer = null;
	public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();

	private static LayerManager m_LayerMgr = new LayerManager();

	public void deleteCache(byte[] ip){
		int rowCount = arpCacheTableModel.getRowCount();

		String inputIp = ipByteToString(ip);
		String storedIp;
		for (int row = 0; row < rowCount; row++) {
			storedIp = (String)arpCacheTableModel.getValueAt(row, 1);
			if(storedIp.equals(inputIp)){
				arpCacheTableModel.removeRow(row);
				return;
			}
		}
	}


	// create send using thread
	public class Send extends Thread{
		byte input[] = {0};

		public void setInput(byte[] input) {
			this.input = input;
		}

		public void run(){
			tcpLayer.ARPSend(input, 0);
		}
	}


	// 테이블에 arp를 추가한다.
	private void addArpToTable(ARPLayer.ARPCache arpCache){
		String ipAddress = ipByteToString(arpCache.getIpAddress());
		String macAddress = arpCache.Status() ? macToString(arpCache.getMacAddress()) : "????";
		String status = arpCache.Status() ? "Complete" : "Incomplete";
		arpCacheTableModel.addRow(new String[]{
				arpCache.getInterfaceName(),
				ipAddress,
				macAddress,
				status});
	}


	// 추가할 arp를 테이블에서 확인하여 있다면 overrite한다.
	// 아니라면 table에 arp를 추가한다.
	public synchronized void addArpCacheToTable(ARPLayer.ARPCache arpCache){

		int rowCount = arpCacheTableModel.getRowCount();
		String storedIp, macAddress, status;
		String addIp = ipByteToString(arpCache.getIpAddress());
		for (int i = 0; i < rowCount; i++) {
			storedIp = (String)arpCacheTableModel.getValueAt(i, 1);
			if(storedIp.equals(addIp)){
				macAddress = arpCache.Status() ? macToString(arpCache.getMacAddress()) : "????";
				status = arpCache.Status() ? "Complete" : "Incomplete";
				arpCacheTableModel.setValueAt(macAddress, i, 2);
				arpCacheTableModel.setValueAt(status, i, 3);
				return;
			}
		}

		addArpToTable(arpCache);
	}


	// 바이트 배열되 되어있는 맥주소를 문자열로 바꿔둔다.
	public String macToString(byte[] mac) {
		StringBuilder buf = new StringBuilder();

		// 바이트를 한개씩 읽어와서 문자열로 변환해준다.
		for (byte b : mac) {
			if (buf.length() != 0) {
				buf.append(':');
			}
			if (b >= 0 && b < 16) {
				buf.append('0');
			}

			buf.append(Integer.toHexString((b < 0) ? b + 256 : b).toUpperCase());
		}
		return buf.toString();
	}

	public String ipByteToString(byte[] bytes){
		String result = "";
		for(byte raw : bytes){
			result += raw & 0xFF;
			result += ".";
		}
		return result.substring(0, result.length()-1);
	}

	private byte[] parsingSrcMACAddress(String addr) {

		byte[] ret = new byte[6];

		StringTokenizer tokens = new StringTokenizer(addr, "-");

		for (int i = 0; tokens.hasMoreElements(); i++) {

			String temp = tokens.nextToken();

			try {
				ret[i] = Byte.parseByte(temp, 16);
			} catch (NumberFormatException e) {
				int minus = (Integer.parseInt(temp, 16)) - 256;
				ret[i] = (byte) (minus);
			}
		}

		return ret;
	}


	public static void setTcpLayer(TCPLayer tcpLayer) {
		AppLayer.tcpLayer = tcpLayer;
	}

	public static void setIpLayer(IPLayer ipLayer) {
		AppLayer.ipLayer = ipLayer;
	}

	public static void setArpLayer(ARPLayer arpLayer) {
		AppLayer.arpLayer = arpLayer;
	}

	public static void setEthernetLayer(EthernetLayer ethernetLayer) {
		AppLayer.ethernetLayer = ethernetLayer;
	}

	public static void setNiLayer(NILayer niLayer) {
		AppLayer.niLayer = niLayer;
	}

	/**
	 * Create the frame.
	 */
	public AppLayer(String pName) {
		this.pLayerName = pName;
		setTitle("TestARP");
		setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
		setBounds(500, 400, 1000, 400);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(null);
		contentPane.setBackground(Color.DARK_GRAY);
		setContentPane(contentPane);

		// ARP Cache Panel------------------------------------------------------
		JPanel arpCachePanel = new JPanel();
		arpCachePanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "ARP Cache",
				TitledBorder.LEADING, TitledBorder.TOP, null, new Color(255, 255, 255)));

		arpCachePanel.setBounds(5, 5, 480, 313);
		arpCachePanel.setBackground(Color.DARK_GRAY);
		contentPane.add(arpCachePanel);
		arpCachePanel.setLayout(null);

		JButton btnItemDelete = new JButton("Delete Item");
		btnItemDelete.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				String ip;
				int row = arpCacheTable.getSelectedRow() ;
				if (row >= 0) {
					ip = (String)arpCacheTableModel.getValueAt(row, 1);
					arpCacheTableModel.removeRow(row);
					try {
						byte[] bytesIp = InetAddress.getByName(ip.trim()).getAddress();
						ARPLayer.ARPCacheTable.remove(bytesIp);
					} catch (UnknownHostException e) {
						e.printStackTrace();
					}
				}
			}
		});
		btnItemDelete.setBounds(10, 230, 225, 30);
		btnItemDelete.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnItemDelete.setFocusPainted(false);
		btnItemDelete.setBackground(Color.DARK_GRAY);
		btnItemDelete.setForeground(Color.WHITE);
		arpCachePanel.add(btnItemDelete);

		JButton btnAllDelete = new JButton("Delete All");
		btnAllDelete.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				int count = arpCacheTableModel.getRowCount();
				for (int i = 0; i < count; i++) {
					arpCacheTableModel.removeRow(0);
				}
				ARPLayer.ARPCacheTable.removeAll();
			}
		});
		btnAllDelete.setBounds(245, 230, 225, 30);
		btnAllDelete.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnAllDelete.setFocusPainted(false);
		btnAllDelete.setBackground(Color.DARK_GRAY);
		btnAllDelete.setForeground(Color.WHITE);
		arpCachePanel.add(btnAllDelete);

		JLabel ipAddressLbl = new JLabel("IP 주소");
		ipAddressLbl.setBounds(10, 272, 45, 30);
		ipAddressLbl.setForeground(Color.WHITE);
		arpCachePanel.add(ipAddressLbl);

		ipAddressTextField = new JTextField();
		ipAddressTextField.setBounds(57, 272, 336, 30);
		ipAddressTextField.setBackground(Color.DARK_GRAY);
		ipAddressTextField.setForeground(Color.WHITE);
		arpCachePanel.add(ipAddressTextField);
		ipAddressTextField.setColumns(10);

		JButton btnCacheSend = new JButton("Send");
		btnCacheSend.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String arpTemp[] = { ipAddressTextField.getText(), arpEthernetAddress, arpStatus };

				try {
					InetAddress destIp = InetAddress.getByName(arpTemp[0].trim());
					byte[] bytesIp = destIp.getAddress();

					// 입력된 값을 토대로 목적지 IP를 설정한다.
					ipLayer.setDestIP(bytesIp);
					arpLayer.setDstIp(bytesIp);

					// send를 시작한다.
					Send send = new Send();
					send.start();

				} catch (Exception e1) {
					e1.printStackTrace();
				}


				ipAddressTextField.setText("");
			}
		});

		btnCacheSend.setBounds(405, 272, 65, 30);
		btnCacheSend.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnCacheSend.setFocusPainted(false);
		btnCacheSend.setBackground(Color.DARK_GRAY);
		btnCacheSend.setForeground(Color.WHITE);
		arpCachePanel.add(btnCacheSend);

		arpCacheTableModel = new DefaultTableModel(arpCacheTableContents, arpCacheTableHeader);
		arpCacheTable = new JTable(arpCacheTableModel);
		arpCacheTable.setShowHorizontalLines(false);
		arpCacheTable.setBackground(Color.DARK_GRAY);
		arpCacheTable.setForeground(Color.white);
		arpCacheScrollPane = new JScrollPane(arpCacheTable);
		arpCacheScrollPane.setBounds(10, 20, 460, 203);
		arpCacheScrollPane.getViewport().setBackground(Color.DARK_GRAY);
		arpCacheScrollPane.getViewport().setForeground(Color.WHITE);
		arpCacheTable.setShowGrid(false);
		arpTableRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		arpTableHeader = arpCacheTable.getTableHeader();
		arpTableHeader.setBackground(Color.DARK_GRAY);
		arpTableHeader.setForeground(Color.WHITE);
		arpColumnModel = arpCacheTable.getColumnModel();
		for (int i = 0; i < arpColumnModel.getColumnCount(); i++)
			arpColumnModel.getColumn(i).setCellRenderer(arpTableRenderer);
		arpCacheTable.getColumnModel().getColumn(3).setPreferredWidth(100);
		arpCachePanel.add(arpCacheScrollPane);

		// Proxy ARP Panel------------------------------------------------------
		JPanel proxyArpPanel = new JPanel();
		proxyArpPanel.setLayout(null);
		proxyArpPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Proxy ARP Entry",
				TitledBorder.LEADING, TitledBorder.TOP, null, new Color(255, 255, 255)));
		proxyArpPanel.setBounds(495, 5, 480, 240);
		proxyArpPanel.setBackground(Color.DARK_GRAY);
		contentPane.add(proxyArpPanel);

		proxyArpTableModel = new DefaultTableModel(proxyArpTableContents, proxyArpTableHeader);
		proxyArpTable = new JTable(proxyArpTableModel);
		proxyArpScrollPane = new JScrollPane(proxyArpTable);
		proxyArpScrollPane.setBounds(10, 20, 460, 165);
		proxyArpScrollPane.getViewport().setBackground(Color.DARK_GRAY);
		proxyArpScrollPane.getViewport().setForeground(Color.WHITE);
		proxyArpTable.setShowGrid(false);
		proxyArpTable.setBackground(Color.DARK_GRAY);
		proxyArpTable.setForeground(Color.white);
		proxyTableRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		proxyTableHeader = proxyArpTable.getTableHeader();
		proxyTableHeader.setBackground(Color.DARK_GRAY);
		proxyTableHeader.setForeground(Color.WHITE);
		proxyColumnModel = proxyArpTable.getColumnModel();
		for (int i = 0; i < proxyColumnModel.getColumnCount(); i++)
			proxyColumnModel.getColumn(i).setCellRenderer(proxyTableRenderer);
		proxyArpPanel.add(proxyArpScrollPane);

		JButton btnProxyArpAdd = new JButton("Add");
		btnProxyArpAdd.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				AddDialog addDialog = new AddDialog();
				addDialog.setVisible(true);
			}
		});
		btnProxyArpAdd.setBounds(10, 195, 225, 30);
		btnProxyArpAdd.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnProxyArpAdd.setFocusPainted(false);
		btnProxyArpAdd.setBackground(Color.DARK_GRAY);
		btnProxyArpAdd.setForeground(Color.WHITE);
		proxyArpPanel.add(btnProxyArpAdd);

		JButton btnProxyArpDelete = new JButton("Delete");
		btnProxyArpDelete.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String ip;
				int row = proxyArpTable.getSelectedRow() ;
				if (row >= 0) {
					ip = (String)proxyArpTable.getValueAt(row, 1);
					proxyArpTableModel.removeRow(proxyArpTable.getSelectedRow());
					try {
						byte[] bytesIp = InetAddress.getByName(ip.trim()).getAddress();
						ARPLayer.ProxyARPEntry.remove(bytesIp);
					} catch (Exception exception) {
						exception.printStackTrace();
					}
				}
			}
		});
		btnProxyArpDelete.setBounds(245, 195, 225, 30);
		btnProxyArpDelete.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnProxyArpDelete.setFocusPainted(false);
		btnProxyArpDelete.setBackground(Color.DARK_GRAY);
		btnProxyArpDelete.setForeground(Color.WHITE);
		proxyArpPanel.add(btnProxyArpDelete);

		// Gratuitious ARP Panel----------------------------------------------
		JPanel gratuitiousArpPanel = new JPanel();
		gratuitiousArpPanel.setLayout(null);
		gratuitiousArpPanel.setBorder(new TitledBorder(UIManager.getBorder("TitledBorder.border"), "Gratuitious ARP",
				TitledBorder.LEADING, TitledBorder.TOP, null, new Color(255, 255, 255)));
		gratuitiousArpPanel.setBounds(495, 255, 477, 63);
		gratuitiousArpPanel.setBackground(Color.DARK_GRAY);
		contentPane.add(gratuitiousArpPanel);

		JLabel gratuitiousArpLbl = new JLabel("H/W 주소");
		gratuitiousArpLbl.setBounds(10, 20, 55, 30);
		gratuitiousArpLbl.setForeground(Color.WHITE);
		gratuitiousArpPanel.add(gratuitiousArpLbl);

		gratuitiousArpTextField = new JTextField();
		gratuitiousArpTextField.setBounds(71, 20, 319, 30);
		gratuitiousArpTextField.setBackground(Color.DARK_GRAY);
		gratuitiousArpTextField.setForeground(Color.WHITE);
		gratuitiousArpPanel.add(gratuitiousArpTextField);
		gratuitiousArpTextField.setColumns(10);

		JButton btnGratuitiousSend = new JButton("Send");
		btnGratuitiousSend.setBounds(402, 20, 65, 30);
		btnGratuitiousSend.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnGratuitiousSend.setFocusPainted(false);
		btnGratuitiousSend.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				byte[] mac = parsingSrcMACAddress(gratuitiousArpTextField.getText());
				arpLayer.setSrcMac(mac);
				arpLayer.setDstIp(arpLayer.getSrcIp());
				ethernetLayer.setSrcAddr(mac);
				gratuitiousArpTextField.setText("");
				// send를 시작한다.
				Send send = new Send();
				send.run();
			}
		});
		btnGratuitiousSend.setBackground(Color.DARK_GRAY);
		btnGratuitiousSend.setForeground(Color.white);
		gratuitiousArpPanel.add(btnGratuitiousSend);

		// Program panel------------------------------------------------------
		JButton btnProgramEnd = new JButton("종료");
		btnProgramEnd.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
				setVisible(false);
			}
		});
		btnProgramEnd.setBounds(395, 324, 90, 27);
		btnProgramEnd.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnProgramEnd.setFocusPainted(false);
		btnProgramEnd.setBackground(Color.DARK_GRAY);
		btnProgramEnd.setForeground(Color.white);
		contentPane.add(btnProgramEnd);

		JButton btnCancel = new JButton("취소");
		btnCancel.addActionListener(new java.awt.event.ActionListener() {
			public void actionPerformed(ActionEvent e) {
			}
		});
		btnCancel.setBounds(495, 324, 90, 27);
		btnCancel.setBorder(new BevelBorder(BevelBorder.RAISED));
		btnCancel.setFocusPainted(false);
		btnCancel.setBackground(Color.DARK_GRAY);
		btnCancel.setForeground(Color.white);
		contentPane.add(btnCancel);
	}

	public errorDialog getErrorDialog(String log) {
		return new errorDialog(log);
	}
	public class errorDialog extends JDialog {

		public errorDialog(String log) {
			setTitle("Error");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setBounds(600, 500, 250, 110);
			errorPane = new JPanel();
			errorPane.setBorder(new EmptyBorder(5, 5, 5, 5));
			errorPane.setBackground(Color.DARK_GRAY);
			errorPane.setForeground(Color.WHITE);
			setContentPane(errorPane);
			errorPane.setLayout(null);

			JLabel messege = new JLabel(log);
			messege.setBounds(40, 10, 200, 15);
			messege.setForeground(Color.white);
			messege.setBackground(Color.DARK_GRAY);
			errorPane.add(messege);

			JButton btnOk = new JButton("OK");
			btnOk.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnOk.setBounds(85, 35, 60, 25);
			btnOk.setBackground(Color.DARK_GRAY);
			btnOk.setForeground(Color.WHITE);
			btnOk.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnOk.setFocusPainted(false);
			errorPane.add(btnOk);

		}
	}

	public class AddDialog extends JFrame {
		String[] deviceList = { "Interface 0", "Interface 1" };

		public AddDialog() {
			setTitle("Proxy ARP 추가");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setBounds(550, 450, 300, 210);
			dialogPane = new JPanel();
			dialogPane.setBorder(new EmptyBorder(5, 5, 5, 5));
			dialogPane.setBackground(Color.DARK_GRAY);
			setContentPane(dialogPane);
			dialogPane.setLayout(null);

			JLabel lblDevice = new JLabel("Device");
			lblDevice.setBounds(50, 20, 40, 15);
			lblDevice.setForeground(Color.WHITE);
			dialogPane.add(lblDevice);

			JLabel lblIpAddress = new JLabel("IP 주소");
			lblIpAddress.setBounds(50, 60, 40, 15);
			lblIpAddress.setForeground(Color.WHITE);
			dialogPane.add(lblIpAddress);

			JLabel lblEthernetAddress = new JLabel("Ethernet 주소");
			lblEthernetAddress.setBounds(12, 100, 78, 15);
			lblEthernetAddress.setForeground(Color.WHITE);
			dialogPane.add(lblEthernetAddress);

			comboBoxDevice = new JComboBox<String>(deviceList);
			comboBoxDevice.setBounds(110, 20, 150, 21);
			comboBoxDevice.setBackground(Color.DARK_GRAY);
			comboBoxDevice.setForeground(Color.WHITE);
			dialogPane.add(comboBoxDevice);

			ipAddressDlgTF = new JTextField();
			ipAddressDlgTF.setBounds(110, 60, 150, 21);
			ipAddressDlgTF.setBackground(Color.DARK_GRAY);
			ipAddressDlgTF.setForeground(Color.white);
			dialogPane.add(ipAddressDlgTF);
			ipAddressDlgTF.setColumns(10);

			ethernetAddressDlgTF = new JTextField();
			ethernetAddressDlgTF.setColumns(10);
			ethernetAddressDlgTF.setBounds(110, 100, 150, 21);
			ethernetAddressDlgTF.setBackground(Color.DARK_GRAY);
			ethernetAddressDlgTF.setForeground(Color.white);
			dialogPane.add(ethernetAddressDlgTF);

			JButton btnOk = new JButton("OK");
			btnOk.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String temp[] = { (String) comboBoxDevice.getSelectedItem(), ipAddressDlgTF.getText(),
							ethernetAddressDlgTF.getText() };

					proxyArpTableModel.addRow(temp);

					try {
						InetAddress srcIp = InetAddress.getByName(temp[1].trim());
						byte[] bytesIp = srcIp.getAddress();

						ARPLayer.Proxy proxy = arpLayer.getProxy(temp[0], bytesIp, parsingSrcMACAddress(temp[2].trim()));
						ARPLayer.ProxyARPEntry.entry.add(proxy);
					} catch (UnknownHostException e1) {
						e1.printStackTrace();
					}
					dispose();
				}
			});
			btnOk.setBounds(41, 140, 97, 23);
			btnOk.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnOk.setFocusPainted(false);
			btnOk.setBackground(Color.DARK_GRAY);
			btnOk.setForeground(Color.white);
			dialogPane.add(btnOk);

			JButton btnCancel = new JButton("Cancel");
			btnCancel.addActionListener(new java.awt.event.ActionListener() {

				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnCancel.setBounds(150,140,97,23);
			btnCancel.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnCancel.setFocusPainted(false);
			btnCancel.setBackground(Color.DARK_GRAY);
			btnCancel.setForeground(Color.WHITE);
			dialogPane.add(btnCancel);
		}
	}




	@Override
	public void SetUnderLayer(BaseLayer pUnderLayer) {
		// TODO Auto-generated method stub
		if (pUnderLayer == null)
			return;
		this.p_UnderLayer = pUnderLayer;
	}

	@Override
	public void SetUpperLayer(BaseLayer pUpperLayer) {
		// TODO Auto-generated method stub
		if (pUpperLayer == null)
			return;
		this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
		// nUpperLayerCount++;
	}

	@Override
	public String GetLayerName() {
		// TODO Auto-generated method stub
		return pLayerName;
	}

	@Override
	public BaseLayer GetUnderLayer() {
		// TODO Auto-generated method stub
		if (p_UnderLayer == null)
			return null;
		return p_UnderLayer;
	}

	@Override
	public BaseLayer GetUpperLayer(int nindex) {
		// TODO Auto-generated method stub
		if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
			return null;
		return p_aUpperLayer.get(nindex);
	}

	@Override
	public void SetUpperUnderLayer(BaseLayer pUULayer) {
		this.SetUpperLayer(pUULayer);
		pUULayer.SetUnderLayer(this);

	}
}
