import org.jnetpcap.PcapIf;

import java.awt.Color;
import java.awt.EventQueue;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import java.awt.Font;

import java.awt.event.ActionListener;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.awt.event.ActionEvent;

public class RoutingDlg extends JFrame implements BaseLayer {

	private JPanel contentPane, routingDialogPane, proxyDialogPane;
	JTable tableRouting, tableCache, tableProxy;
	DefaultTableModel tableModelRouting, tableModelCache, tableModelProxy;
	JScrollPane scrollRouting, scrollCache, scrollProxy;
	JTableHeader tableHeaderRouting, tableHeaderCache, tableHeaderProxy;
	DefaultTableCellRenderer tableRendererRouting = new DefaultTableCellRenderer();
	DefaultTableCellRenderer tableRendererCache = new DefaultTableCellRenderer();
	DefaultTableCellRenderer tableRendererProxy = new DefaultTableCellRenderer();
	TableColumnModel columnModelRouting, columnModelCache, columnModelProxy;

	JComboBox comboBoxInterface, comboBoxDevice;

	JTextField textFieldNetmask, textFieldGateway, textFieldDestination;
	JTextField textFieldIpAddress, textFieldEthernetAddress;

	final String[] tableHeaderStsticRouting = { "Destination", "NetMask", "Gateway", "Flag", "Interface", "Metric" };
	final String[] tableHeaderArpCache = { "IP Address", "Ethernet Address", "Interface", "Flag" };
	final String[] tableHeaderProxyArp = { "IP Address", "Ethernet Address", "Interface" };
	String[][] tableContentsRouting = new String[0][3];
	String[][] tableContentsCache = new String[0][3];
	String[][] tableContentsProxy = new String[0][3];

	// field for layer
	static IPLayer[] ipLayer;
	static ARPLayer[] arpLayer;
	static EthernetLayer[] ethernetLayer;
	static NILayer[] niLayer;

	public String pLayerName = null;
	public int nUpperLayerCount = 0;
	public BaseLayer p_UnderLayer = null;
	public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();

	private static LayerManager m_LayerMgr = new LayerManager();

	// create send using thread
	public class Send extends Thread {
		byte input[] = { 0 };

		public void setInput(byte[] input) {
			this.input = input;
		}

		public void run() {
//			tcpLayer.Send(input, 0);
		}
	}

	// 테이블에 arp를 추가한다.
	private void addArpToTable(ARPLayer.ARPCache arpCache) {
		String ipAddress = ipByteToString(arpCache.getIpAddress());
		String macAddress = arpCache.Status() ? macToString(arpCache.getMacAddress()) : "????";
		String status = arpCache.Status() ? "Complete" : "Incomplete";
		tableModelCache.addRow(new String[] { arpCache.getInterfaceName(), ipAddress, macAddress, status });
	}

	// 추가할 arp를 테이블에서 확인하여 있다면 overrite한다.
	// 아니라면 table에 arp를 추가한다.
	public synchronized void addArpCacheToTable(ARPLayer.ARPCache arpCache) {

		int rowCount = tableModelCache.getRowCount();
		String storedIp, macAddress, status;
		String addIp = ipByteToString(arpCache.getIpAddress());
		for (int i = 0; i < rowCount; i++) {
			storedIp = (String) tableModelCache.getValueAt(i, 1);
			if (storedIp.equals(addIp)) {
				macAddress = arpCache.Status() ? macToString(arpCache.getMacAddress()) : "????";
				status = arpCache.Status() ? "Complete" : "Incomplete";
				tableModelCache.setValueAt(macAddress, i, 2);
				tableModelCache.setValueAt(status, i, 3);
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

	public String ipByteToString(byte[] bytes) {
		String result = "";
		for (byte raw : bytes) {
			result += raw & 0xFF;
			result += ".";
		}
		return result.substring(0, result.length() - 1);
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

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		RoutingDlg routingDlg;
		routingDlg = new RoutingDlg("Routing");
	
		try {
			m_LayerMgr.AddLayer(routingDlg);

			ipLayer[0] = new IPLayer("Ip0");
			m_LayerMgr.AddLayer(ipLayer[0]);
			ipLayer[1] = new IPLayer("Ip1");
			m_LayerMgr.AddLayer(ipLayer[1]);

			arpLayer[0] = new ARPLayer("Arp0");
			m_LayerMgr.AddLayer(arpLayer[0]);
			arpLayer[1] = new ARPLayer("Arp1");
			m_LayerMgr.AddLayer(arpLayer[1]);

			ethernetLayer[0] = new EthernetLayer("Ethernet0");
			m_LayerMgr.AddLayer(ethernetLayer[0]);
			ethernetLayer[1] = new EthernetLayer("Ethernet1");
			m_LayerMgr.AddLayer(ethernetLayer[1]);

			niLayer[0] = new NILayer("NI0");
			m_LayerMgr.AddLayer(niLayer[0]);
			niLayer[1] = new NILayer("NI1");
			m_LayerMgr.AddLayer(niLayer[1]);

			m_LayerMgr.ConnectLayers(" NI0 ( *Ethernet0 ( *Ip0 ) ) ");
			m_LayerMgr.GetLayer("Ip0").SetUnderLayer(m_LayerMgr.GetLayer("Arp0"));
			m_LayerMgr.GetLayer("Ethernet0").SetUpperUnderLayer(m_LayerMgr.GetLayer("Arp0"));


			m_LayerMgr.GetLayer("NI1").SetUpperUnderLayer(m_LayerMgr.GetLayer("Ethernet1"));
			m_LayerMgr.GetLayer("Ethernet1").SetUpperUnderLayer(m_LayerMgr.GetLayer("Ip1"));
			m_LayerMgr.GetLayer("Ip1").SetUnderLayer(m_LayerMgr.GetLayer("Arp1"));
			m_LayerMgr.GetLayer("Ethernet1").SetUpperUnderLayer(m_LayerMgr.GetLayer("Arp1"));

			// ip레이어에 이더넷레이어 설정
			ipLayer[0].setEthernetLayer(ethernetLayer[0]);
			ipLayer[1].setEthernetLayer(ethernetLayer[1]);

			arpLayer[0].setRoutingDlg(routingDlg);
			arpLayer[1].setRoutingDlg(routingDlg);

//			각 NIC에 해당하는 MAC Address와 IP Address 를 set
			int index = 0;
			Enumeration<NetworkInterface> eNI = NetworkInterface.getNetworkInterfaces();
			while(eNI.hasMoreElements()) {
				NetworkInterface ni = eNI.nextElement();
//				ni를 사용 중이고 루프백이 아니면
				if(ni.isUp() && !ni.isLoopback()) {
					Enumeration<InetAddress> eIA = ni.getInetAddresses();
					while(eIA.hasMoreElements()) {
						InetAddress ia = eIA.nextElement();
						if(ia instanceof Inet4Address) {
//							ipv4의 경우만 세팅
							ethernetLayer[index].setSrcAddr(ni.getHardwareAddress());
							arpLayer[index].setSrcMac(ni.getHardwareAddress());
							arpLayer[index].setSrcIp(ia.getAddress());
							ipLayer[index].setSrcIP(ia.getAddress());
						}
					}
					while(true) {
						int iNum = 0;
						boolean nicFound = false;
						for(PcapIf nic : niLayer[index].m_pAdapterList) {
							if(Arrays.equals(nic.getHardwareAddress(), ni.getHardwareAddress())) {
//								nilayer 어댑터 세팅
								niLayer[index].SetAdapterNumber(iNum);
								nicFound = true;
								break;
							}
							iNum++;
						}
						if(nicFound)
							break;
						niLayer[index].SetAdapterList();
					}
					if(++index > 1)
						break;
				}
			}

			ethernetLayer[0].SetUpperLayer(ipLayer[0]);
			ethernetLayer[1].SetUpperLayer(ipLayer[1]);

			// 어떤 어댑터를 사용할지 결정한다.
			// 디버깅을 통해 adapter list 를 이용하여 설정한다.
			// 링크가 다 연결된 후 언더레이어 접근할수 있어서 이 때 접근해준다.
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public RoutingDlg(String pName) {
		this.pLayerName = pName;
		setTitle("Static Router");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 1000, 500);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);

		JPanel panelRouting = new JPanel();
		panelRouting.setBounds(10, 10, 580, 440);
		contentPane.add(panelRouting);
		panelRouting.setLayout(null);

		JLabel lblRoutingTable = new JLabel("Static Routing Table");
		lblRoutingTable.setBounds(178, 10, 201, 31);
		lblRoutingTable.setFont(new Font("굴림", Font.PLAIN, 20));
		panelRouting.add(lblRoutingTable);

		tableModelRouting = new DefaultTableModel(tableContentsRouting, tableHeaderStsticRouting);
		tableRouting = new JTable(tableModelRouting);
		tableRouting.setShowHorizontalLines(false);
		tableRouting.setShowGrid(false);
		scrollRouting = new JScrollPane(tableRouting);
		scrollRouting.setBounds(10, 50, 556, 338);
		tableRendererRouting.setHorizontalAlignment(SwingConstants.CENTER);
		tableHeaderRouting = tableRouting.getTableHeader();
		columnModelRouting = tableRouting.getColumnModel();
		for (int i = 0; i < tableModelRouting.getColumnCount(); i++)
			columnModelRouting.getColumn(i).setCellRenderer(tableRendererRouting);
		panelRouting.add(scrollRouting);

		JButton btnRoutingAdd = new JButton("Add");
		btnRoutingAdd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				routingAddDialog routingAddDialog = new routingAddDialog();
				routingAddDialog.setVisible(true);

			}
		});
		btnRoutingAdd.setBounds(167, 399, 111, 31);
		panelRouting.add(btnRoutingAdd);

		JButton btnRoutingDelete = new JButton("Delete");
		btnRoutingDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String destIp;
				int row = tableRouting.getSelectedRow() ;
				if (row >= 0) {
					destIp = (String)tableRouting.getValueAt(row, 0);
					tableModelRouting.removeRow(tableRouting.getSelectedRow());
					try {
						byte[] bytesIp = InetAddress.getByName(destIp.trim()).getAddress();
						ARPLayer.ProxyARPEntry.remove(bytesIp);
					} catch (Exception exception) {
						exception.printStackTrace();
					}
				}
			}
		});
		btnRoutingDelete.setBounds(298, 399, 111, 31);
		panelRouting.add(btnRoutingDelete);

		JPanel panelCache = new JPanel();
		panelCache.setBounds(602, 10, 370, 220);
		contentPane.add(panelCache);
		panelCache.setLayout(null);

		JLabel lblArpCacheTable = new JLabel("ARP Cache Table");
		lblArpCacheTable.setFont(new Font("굴림", Font.PLAIN, 20));
		lblArpCacheTable.setBounds(114, 10, 192, 30);
		panelCache.add(lblArpCacheTable);

		JButton btnCacheDelete = new JButton("Delete");
		btnCacheDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String ip;
				int row = tableCache.getSelectedRow() ;
				if (row >= 0) {
					ip = (String)tableModelCache.getValueAt(row, 1);
					tableModelCache.removeRow(row);
					try {
						byte[] bytesIp = InetAddress.getByName(ip.trim()).getAddress();
						ARPLayer.ARPCacheTable.remove(bytesIp);
					} catch (UnknownHostException event) {
						event.printStackTrace();
					}
				}
			}
		});
		btnCacheDelete.setBounds(136, 179, 111, 31);
		panelCache.add(btnCacheDelete);

		tableModelCache = new DefaultTableModel(tableContentsCache, tableHeaderArpCache);
		tableCache = new JTable(tableModelCache);
		tableCache.setShowHorizontalLines(false);
		tableCache.setShowGrid(false);
		scrollCache = new JScrollPane(tableCache);
		scrollCache.setBounds(12, 42, 346, 131);
		tableRendererCache.setHorizontalAlignment(SwingConstants.CENTER);
		tableHeaderCache = tableCache.getTableHeader();
		columnModelCache = tableCache.getColumnModel();
		for (int i = 0; i < tableModelCache.getColumnCount(); i++)
			columnModelCache.getColumn(i).setCellRenderer(tableRendererCache);
		panelCache.add(scrollCache);

		JPanel panelProxy = new JPanel();
		panelProxy.setBounds(602, 240, 370, 210);
		contentPane.add(panelProxy);
		panelProxy.setLayout(null);

		JLabel lblProxyArpTable = new JLabel("Proxy ARP Table");
		lblProxyArpTable.setFont(new Font("굴림", Font.PLAIN, 20));
		lblProxyArpTable.setBounds(110, 10, 159, 29);
		panelProxy.add(lblProxyArpTable);

		JButton btnProxyDelete = new JButton("Delete");
		btnProxyDelete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				String ip;
				int row = tableProxy.getSelectedRow() ;
				if (row >= 0) {
					ip = (String)tableProxy.getValueAt(row, 1);
					tableModelProxy.removeRow(tableProxy.getSelectedRow());
					try {
						byte[] bytesIp = InetAddress.getByName(ip.trim()).getAddress();
						ARPLayer.ProxyARPEntry.remove(bytesIp);
					} catch (Exception exception) {
						exception.printStackTrace();
					}
				}
			}
		});
		btnProxyDelete.setBounds(214, 173, 111, 31);
		panelProxy.add(btnProxyDelete);

		tableModelProxy = new DefaultTableModel(tableContentsProxy, tableHeaderProxyArp);
		tableProxy = new JTable(tableModelProxy);
		tableProxy.setShowHorizontalLines(false);
		tableProxy.setShowGrid(false);
		scrollProxy = new JScrollPane(tableProxy);
		scrollProxy.setBounds(12, 39, 346, 120);
		tableRendererProxy.setHorizontalAlignment(SwingConstants.CENTER);
		tableHeaderProxy = tableProxy.getTableHeader();
		columnModelProxy = tableProxy.getColumnModel();
		for (int i = 0; i < tableModelProxy.getColumnCount(); i++)
			columnModelProxy.getColumn(i).setCellRenderer(tableRendererProxy);
		panelProxy.add(scrollProxy);

		JButton btnProxyAdd = new JButton("Add");
		btnProxyAdd.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				proxyAddDialog proxyAddDialog = new proxyAddDialog();
				proxyAddDialog.setVisible(true);
			}
		});
		btnProxyAdd.setBounds(64, 173, 111, 31);
		panelProxy.add(btnProxyAdd);
		setVisible(true);

		ipLayer = new IPLayer[2];
		arpLayer = new ARPLayer[2];
		ethernetLayer = new EthernetLayer[2];
		niLayer = new NILayer[2];
	}

	JRadioButton rdbtnUp;
	JRadioButton rdbtnGateway;
	JRadioButton rdbtnHost;
	public class routingAddDialog extends JFrame {
		String[] deviceList = { "Interface 0", "Interface 1" };

		public routingAddDialog() {
			setTitle("Adding Routing Table");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setBounds(550, 450, 352, 325);
			routingDialogPane = new JPanel();
			routingDialogPane.setBorder(new EmptyBorder(5, 5, 5, 5));
			routingDialogPane.setBackground(Color.DARK_GRAY);
			setContentPane(routingDialogPane);
			routingDialogPane.setLayout(null);

			JLabel lblDestination = new JLabel("Destination");
			lblDestination.setBounds(12, 23, 67, 15);
			lblDestination.setForeground(Color.WHITE);
			routingDialogPane.add(lblDestination);

			JLabel lblNetmask = new JLabel("Netmask");
			lblNetmask.setBounds(12, 63, 67, 15);
			lblNetmask.setForeground(Color.WHITE);
			routingDialogPane.add(lblNetmask);

			JLabel lblGateway = new JLabel("Gateway");
			lblGateway.setBounds(12, 103, 78, 15);
			lblGateway.setForeground(Color.WHITE);
			routingDialogPane.add(lblGateway);

			comboBoxInterface = new JComboBox<String>(deviceList);
			comboBoxInterface.setBounds(110, 184, 204, 21);
			comboBoxInterface.setBackground(Color.DARK_GRAY);
			comboBoxInterface.setForeground(Color.WHITE);
			routingDialogPane.add(comboBoxInterface);

			textFieldNetmask = new JTextField();
			textFieldNetmask.setBounds(110, 60, 204, 21);
			textFieldNetmask.setBackground(Color.DARK_GRAY);
			textFieldNetmask.setForeground(Color.white);
			routingDialogPane.add(textFieldNetmask);
			textFieldNetmask.setColumns(10);

			textFieldGateway = new JTextField();
			textFieldGateway.setColumns(10);
			textFieldGateway.setBounds(110, 100, 204, 21);
			textFieldGateway.setBackground(Color.DARK_GRAY);
			textFieldGateway.setForeground(Color.white);
			routingDialogPane.add(textFieldGateway);

			JButton btnAdd = new JButton("추가");
			btnAdd.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					String ipAddr = textFieldDestination.getText().trim();
					String netMask = textFieldNetmask.getText().trim();
					String gateway = textFieldGateway.getText().trim();

					InetAddress ip = null;
					InetAddress gateWayIp = null;
					try {
						int fullCount = 32;
						int zeroCount = 0;
						int intNetmask;
						long mask = ipToLong(netMask);
						for (int i = 0; i < 32; i++) {
							if((mask & 1) == 0){
								zeroCount++;
							}else{
								break;
							}
							mask  = mask >> 1;
						}
						ip = InetAddress.getByName(ipAddr);
						gateWayIp = InetAddress.getByName(gateway);
						intNetmask = fullCount - zeroCount;
						boolean[] flag = new boolean[3];
						flag[0] = rdbtnUp.isSelected();
						flag[1] = rdbtnGateway.isSelected();
						flag[2] = rdbtnHost.isSelected();
						System.out.println(fullCount - zeroCount);

						RoutingTable tbl = RoutingTable.getInstance();
						tbl.add(tbl.getRoutingTableRow(ip.getAddress(), intNetmask, gateWayIp.getAddress(), flag, "interface1", 2));

						tableModelRouting.setRowCount(0);

						List<String[]> rows = tbl.GetTblRows();

						for(String[] row : rows){
							tableModelRouting.addRow(row);
						}

					} catch (UnknownHostException e1) {
						e1.printStackTrace();
					}
				}
			});
			btnAdd.setBounds(54, 231, 97, 23);
			btnAdd.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnAdd.setFocusPainted(false);
			btnAdd.setBackground(Color.DARK_GRAY);
			btnAdd.setForeground(Color.white);
			routingDialogPane.add(btnAdd);

			JButton btnCancel = new JButton("취소");
			btnCancel.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnCancel.setBounds(188, 231, 97, 23);
			btnCancel.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnCancel.setFocusPainted(false);
			btnCancel.setBackground(Color.DARK_GRAY);
			btnCancel.setForeground(Color.WHITE);
			routingDialogPane.add(btnCancel);

			JLabel lblFlag = new JLabel("Flag");
			lblFlag.setForeground(Color.WHITE);
			lblFlag.setBounds(12, 147, 78, 15);
			routingDialogPane.add(lblFlag);

			JLabel lblInterface = new JLabel("Interface");
			lblInterface.setForeground(Color.WHITE);
			lblInterface.setBounds(12, 187, 78, 15);
			routingDialogPane.add(lblInterface);

			rdbtnUp = new JRadioButton("UP");
			rdbtnUp.setBounds(110, 143, 41, 23);
			routingDialogPane.add(rdbtnUp);

			rdbtnGateway = new JRadioButton("Gateway");
			rdbtnGateway.setBounds(164, 143, 85, 23);
			routingDialogPane.add(rdbtnGateway);

			rdbtnHost = new JRadioButton("Host");
			rdbtnHost.setBounds(263, 143, 51, 23);
			routingDialogPane.add(rdbtnHost);

			textFieldDestination = new JTextField();
			textFieldDestination.setForeground(Color.WHITE);
			textFieldDestination.setColumns(10);
			textFieldDestination.setBackground(Color.DARK_GRAY);
			textFieldDestination.setBounds(110, 20, 204, 21);
			routingDialogPane.add(textFieldDestination);
		}
	}

	public long ipToLong(String ipAddress) {

		String[] ipAddressInArray = ipAddress.split("\\.");

		long result = 0;
		for (int i = 0; i < ipAddressInArray.length; i++) {

			int power = 3 - i;
			int ip = Integer.parseInt(ipAddressInArray[i]);
			result += ip * Math.pow(256, power);

		}

		return result;
	}

	public class proxyAddDialog extends JFrame {
		String[] deviceList = { "Interface 0", "Interface 1" };

		public proxyAddDialog() {
			setTitle("Proxy ARP 추가");
			setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
			setBounds(550, 450, 300, 210);
			proxyDialogPane = new JPanel();
			proxyDialogPane.setBorder(new EmptyBorder(5, 5, 5, 5));
			proxyDialogPane.setBackground(Color.DARK_GRAY);
			setContentPane(proxyDialogPane);
			proxyDialogPane.setLayout(null);

			JLabel lblDevice = new JLabel("Device");
			lblDevice.setBounds(50, 20, 40, 15);
			lblDevice.setForeground(Color.WHITE);
			proxyDialogPane.add(lblDevice);

			JLabel lblIpAddress = new JLabel("IP 주소");
			lblIpAddress.setBounds(50, 60, 40, 15);
			lblIpAddress.setForeground(Color.WHITE);
			proxyDialogPane.add(lblIpAddress);

			JLabel lblEthernetAddress = new JLabel("Ethernet 주소");
			lblEthernetAddress.setBounds(12, 100, 78, 15);
			lblEthernetAddress.setForeground(Color.WHITE);
			proxyDialogPane.add(lblEthernetAddress);

			comboBoxDevice = new JComboBox<String>(deviceList);
			comboBoxDevice.setBounds(110, 20, 150, 21);
			comboBoxDevice.setBackground(Color.DARK_GRAY);
			comboBoxDevice.setForeground(Color.WHITE);
			proxyDialogPane.add(comboBoxDevice);

			textFieldIpAddress = new JTextField();
			textFieldIpAddress.setBounds(110, 60, 150, 21);
			textFieldIpAddress.setBackground(Color.DARK_GRAY);
			textFieldIpAddress.setForeground(Color.white);
			proxyDialogPane.add(textFieldIpAddress);
			textFieldIpAddress.setColumns(10);

			textFieldEthernetAddress = new JTextField();
			textFieldEthernetAddress.setColumns(10);
			textFieldEthernetAddress.setBounds(110, 100, 150, 21);
			textFieldEthernetAddress.setBackground(Color.DARK_GRAY);
			textFieldEthernetAddress.setForeground(Color.white);
			proxyDialogPane.add(textFieldEthernetAddress);

			JButton btnOk = new JButton("OK");
			btnOk.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					dispose();
					String temp[] = { (String) comboBoxDevice.getSelectedItem(), textFieldIpAddress.getText(),
							textFieldEthernetAddress.getText() };

					tableModelProxy.addRow(temp);

					try {
						InetAddress srcIp = InetAddress.getByName(temp[1].trim());
						byte[] bytesIp = srcIp.getAddress();

						ARPLayer.Proxy proxy = arpLayer[0].getProxy(temp[0], bytesIp, parsingSrcMACAddress(temp[2].trim()));
						ARPLayer.ProxyARPEntry.entry.add(proxy);
					} catch (UnknownHostException e1) {
						e1.printStackTrace();
					}
				}
			});
			btnOk.setBounds(41, 140, 97, 23);
			btnOk.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnOk.setFocusPainted(false);
			btnOk.setBackground(Color.DARK_GRAY);
			btnOk.setForeground(Color.white);
			proxyDialogPane.add(btnOk);

			JButton btnCancel = new JButton("Cancel");
			btnCancel.addActionListener(new ActionListener() {

				public void actionPerformed(ActionEvent e) {
					dispose();
				}
			});
			btnCancel.setBounds(150, 140, 97, 23);
			btnCancel.setBorder(new BevelBorder(BevelBorder.RAISED));
			btnCancel.setFocusPainted(false);
			btnCancel.setBackground(Color.DARK_GRAY);
			btnCancel.setForeground(Color.WHITE);
			proxyDialogPane.add(btnCancel);
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

