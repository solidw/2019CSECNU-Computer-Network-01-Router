package arp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;

public class ARPLayer implements BaseLayer {

    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    String interfaceName;
    static AppLayer appLayer;


    public ARPLayer(String name) {
        pLayerName = name;
        interfaceName = "Interface 0";
    }

    private static class ARPHeader {
        byte[] HWtype = new byte[2];
        byte[] protocol = new byte[2];
        byte HWLength;
        byte protLength;
        byte[] opcode = new byte[2];
        byte[] srcMac = new byte[6];
        byte[] srcIp = new byte[4];
        byte[] dstMac = new byte[6];
        byte[] dstIp = new byte[4];

        public ARPHeader() {
            HWtype[1] = (byte)0x01;

            protocol[0] = (byte) 0x08;

            HWLength = 6;
            protLength = 4;
        }

        public void setOpcode(byte[] opcode) {
            this.opcode = opcode;
        }

        public void setSrcIp(byte[] srcIp) {
            this.srcIp = srcIp;
        }

        public void setDstIp(byte[] dstIp) {
            this.dstIp = dstIp;
        }

        public void setSrcMac(byte[] srcMac) {
            this.srcMac = srcMac;
        }

        public void setDstMac(byte[] dstMac) {
            this.dstMac = dstMac;
        }

        public byte[] getSrcIp() {
            return srcIp;
        }
    }

    ARPHeader m_sHeader = new ARPHeader();

    public void setSrcIp(byte[] srcIp) {
        m_sHeader.setSrcIp(srcIp);
    }

    public void setDstIp(byte[] dstIp) {
        m_sHeader.setDstIp(dstIp);
    }

    public void setSrcMac(byte[] srcMac) {
        m_sHeader.setSrcMac(srcMac);
    }

    public void setDstMac(byte[] dstMac) {
        m_sHeader.setDstMac(dstMac);
    }

    public byte[] getSrcIp() {
        return m_sHeader.getSrcIp();
    }

    private byte[] ObjToByte(ARPHeader Header, byte[] input, int length) {
        byte[] buf = new byte[length + 28];
        System.arraycopy(Header.HWtype, 0, buf, 0, 2);
        System.arraycopy(Header.protocol, 0, buf, 2, 2);
        buf[4] = Header.HWLength;
        buf[5] = Header.protLength;
        System.arraycopy(Header.opcode, 0, buf, 6, 2);
        System.arraycopy(Header.srcMac, 0, buf, 8, 6);
        System.arraycopy(Header.srcIp, 0, buf, 14, 4);
        System.arraycopy(Header.dstMac, 0, buf, 18, 6);
        System.arraycopy(Header.dstIp, 0, buf, 24, 4);
        System.arraycopy(input, 0, buf, 28, length);
        return buf;
    }

    public void setAppLayer(AppLayer app){
        appLayer = app;
    }

    @Override
    public synchronized boolean Send(byte[] input, int length) {
        m_sHeader.setOpcode(new byte[]{(byte)0x00, (byte)0x01});
        m_sHeader.setDstMac(new byte[6]);


        ARPCache addCache = new ARPCache(interfaceName, m_sHeader.dstIp, new byte[6], false);
        if(!Arrays.equals(m_sHeader.srcIp, addCache.getIpAddress())) {
            if(ARPCacheTable.add(addCache)){
                appLayer.addArpCacheToTable(addCache);
            }
        }


        byte[] buf = ObjToByte(m_sHeader, input, length);
        GetUnderLayer().Send(buf, buf.length);
        return true;
    }

    public byte[] swapSrcAndDst(byte[] input, byte[] senderIp, byte[] senderMac, byte[] dstIp, byte[] dstMac) {
        input[7] = 0x02;
        System.arraycopy(dstMac, 0, input, 8, 6);
        System.arraycopy(dstIp, 0, input, 14, 4);
        System.arraycopy(senderMac, 0, input,18, 6);
        System.arraycopy(senderIp, 0, input, 24, 4);
        return input;
    }

    @Override
    public synchronized boolean Receive(byte[] input) {
        byte[] opcode = new byte[2];

        System.arraycopy(input, 6, opcode, 0, 2);

        if(opcode[1] == 0x01) {
            byte[] senderIp = new byte[4];
            byte[] senderMac = new byte[6];
            byte[] dstIp = new byte[4];

            System.arraycopy(input, 8, senderMac, 0, 6);
            System.arraycopy(input, 14, senderIp, 0, 4);
            System.arraycopy(input, 24, dstIp, 0, 4);

            if(!Arrays.equals(senderIp, m_sHeader.srcIp)) {
                ARPCache addCache = new ARPCache(interfaceName, senderIp, senderMac, true);
                if(ARPCacheTable.add(addCache)){
                    appLayer.addArpCacheToTable(addCache);
                }
            }

            if(Arrays.equals(dstIp, m_sHeader.srcIp)) {
                byte[] data = swapSrcAndDst(input, senderIp, senderMac, dstIp, m_sHeader.srcMac);
                GetUnderLayer().Send(data, data.length);
            }

            Proxy proxy = ProxyARPEntry.get(dstIp);
            if(proxy != null) {
                byte[] data = swapSrcAndDst(input, senderIp, senderMac, proxy.IpAddress(), proxy.MacAddress());
                GetUnderLayer().Send(data, data.length);
            }
        }

        if(opcode[1] == 0x02) {
            byte[] senderIp = new byte[4];
            byte[] senderMac = new byte[6];

            System.arraycopy(input, 8, senderMac, 0, 6);
            System.arraycopy(input, 14, senderIp, 0, 4);

            if(Arrays.equals(senderIp, m_sHeader.srcIp)) {
                AppLayer.errorDialog errorDialog = appLayer.getErrorDialog("Duplicated IP");
                errorDialog.setVisible(true);
                return false;
            }

            ARPCache addCache = new ARPCache(interfaceName, senderIp, senderMac, true);
            System.arraycopy(input, 8, senderMac, 0, 6);
            System.arraycopy(input, 14, senderIp, 0, 4);

            if(ARPCacheTable.add(addCache)){
                appLayer.addArpCacheToTable(addCache);
            }
        }
        return true;
    }

    @Override
    public String GetLayerName() {
        return pLayerName;
    }

    @Override
    public BaseLayer GetUnderLayer() {
        if (p_UnderLayer == null)
            return null;
        return p_UnderLayer;
    }

    @Override
    public BaseLayer GetUpperLayer(int nindex) {
        if (nindex < 0 || nindex > nUpperLayerCount || nUpperLayerCount < 0)
            return null;
        return p_aUpperLayer.get(nindex);
    }

    @Override
    public void SetUnderLayer(BaseLayer pUnderLayer) {
        if (pUnderLayer == null)
            return;
        p_UnderLayer = pUnderLayer;
    }

    @Override
    public void SetUpperLayer(BaseLayer pUpperLayer) {
        if (pUpperLayer == null)
            return;
        this.p_aUpperLayer.add(nUpperLayerCount++, pUpperLayer);
    }

    @Override
    public void SetUpperUnderLayer(BaseLayer pUULayer) {
        this.SetUpperLayer(pUULayer);
        pUULayer.SetUnderLayer(this);
    }

    public class ARPCache {
        private String interfaceName;
        private byte[] ipAddress = new byte[4];
        private byte[] macAddress = new byte[6];
        private boolean status;

        public ARPCache(String interfaceName, byte[] ipAddress, byte[] macAddress, boolean status) {
            setInterfaceName(interfaceName)
                    .setIpAddress(ipAddress)
                    .setMacAddress(macAddress)
                    .setStatus(status);
        }

        public String InterfaceName() {
            return interfaceName;
        }

        public byte[] IpAddress() {
            return ipAddress;
        }

        public byte[] MacAddress() {
            return macAddress;
        }

        public boolean Status() {
            return status;
        }


        public ARPCache setStatus(boolean status) {
            this.status = status;
            return this;
        }

        public ARPCache setMacAddress(byte[] macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public ARPCache setIpAddress(byte[] ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public ARPCache setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public byte[] getIpAddress() {
            return ipAddress;
        }

        public byte[] getMacAddress() {
            return macAddress;
        }

        public boolean isStatus() {
            return status;
        }
    }

    public static class ARPCacheTable {
        public static ArrayList<ARPCache> table = new ArrayList<>();

        public AppLayer getAppLayer() {
            return appLayer;
        }


        public static void remove(byte[] ip) {

            Iterator<ARPCache> iter = table.iterator();

            while(iter.hasNext()) {
                ARPCache cache = iter.next();
                if(Arrays.equals(cache.IpAddress(), ip)){
                    iter.remove();
                }
            }
        }

        public static void removeAll() {
            table.clear();
        }



        public static ARPCache getCache(byte[] ip) {
            for (ARPCache item : table) {
                if(Arrays.equals(item.IpAddress(), ip))
                    return item;
            }
            return null;
        }

        public static ArrayList<ARPCache> getTable() {
            return table;
        }

        // table에 arpCache를 추가한다.
        public static boolean add(ARPCache arpCache) {

            ARPCache getCache = getCache(arpCache.IpAddress());

            // 테이블에 없다면 바로 추가한다.
            if(getCache == null){
                table.add(arpCache);
                if(arpCache.status == true) {
                    TimerUtility.SetTimeout(Arrays.toString(arpCache.ipAddress), 1000 * 60 * 20, () -> {
                        remove(arpCache.ipAddress);
                        appLayer.deleteCache(arpCache.ipAddress);
                    });
                }
                else {
                    TimerUtility.SetTimeout(Arrays.toString(arpCache.ipAddress), 1000 * 60 * 3, () -> {
                        remove(arpCache.ipAddress);
                        appLayer.deleteCache(arpCache.ipAddress);
                    });
                }
            // 있지만 mac이 비어있다면 수정한다.
            }
            else if(!Arrays.equals(arpCache.getMacAddress(), getCache.MacAddress())){
                getCache.setMacAddress(arpCache.getMacAddress());
                getCache.setStatus(true);
                TimerUtility.Alter(Arrays.toString(arpCache.ipAddress), 1000 * 60 * 20);
            }
            else if(Arrays.equals(arpCache.getMacAddress(), getCache.MacAddress()) && getCache.status == true){
                TimerUtility.Alter(Arrays.toString(arpCache.ipAddress), 1000 * 60 * 20);
            }
            else {
                return false;
            }

            return true;
        }
    }

    public Proxy getProxy(String name, byte[] ip, byte[] mac){
        return new Proxy(name, ip, mac);
    }

    public class Proxy {
        private String interfaceName;
        private byte[] ipAddress = new byte[4];
        private byte[] macAddress = new byte[6];

        public Proxy(String interfaceName, byte[] ipAddress, byte[] macAddress) {
            setInterfaceName(interfaceName)
                    .setIpAddress(ipAddress)
                    .setMacAddress(macAddress);
        }

        public String InterfaceName() {
            return interfaceName;
        }

        public byte[] IpAddress() {
            return ipAddress;
        }

        public byte[] MacAddress() {
            return macAddress;
        }

        public Proxy setMacAddress(byte[] macAddress) {
            this.macAddress = macAddress;
            return this;
        }

        public Proxy setIpAddress(byte[] ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Proxy setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
            return this;
        }
    }

    public static class ProxyARPEntry {
        public static ArrayList<Proxy> entry = new ArrayList<>();

        public static Proxy get(byte[] ip) {
            for (Proxy item : entry) {
                if(Arrays.equals(item.IpAddress(), ip))
                    return item;
            }
            return null;
        }

        public static void remove(byte[] ip) {
            for (Proxy item : entry) {
                if(Arrays.equals(item.IpAddress(), ip))
                    entry.remove(item);
            }
        }
    }
}
