package arp;

import java.util.ArrayList;
import java.util.Arrays;

public class EthernetLayer implements BaseLayer {

    public class EthernetFrame {

        @Override
        public String toString(){

            StringBuilder res = new StringBuilder();

            for (int i = 0; i < srcAddr.addr.length; i++){
                if(srcAddr.addr[i] < 0){
                    res.append(Integer.toString((int) srcAddr.addr[i] + 256, 16));
                }
                else {
                    res.append(Integer.toString(srcAddr.addr[i], 16));
                }
                if(i != srcAddr.addr.length - 1) res.append(":");
            }

            return res.toString();
        }

        private class EthernetAddr {
            public byte[] addr = new byte[6];

            public EthernetAddr() {
                this.addr[0] = (byte) 0x00;
                this.addr[1] = (byte) 0x00;
                this.addr[2] = (byte) 0x00;
                this.addr[3] = (byte) 0x00;
                this.addr[4] = (byte) 0x00;
                this.addr[5] = (byte) 0x00;
            }
        }

        EthernetAddr dstAddr;
        EthernetAddr srcAddr;

        byte[] type;

        private EthernetFrame() {
            this.dstAddr = new EthernetAddr();
            this.srcAddr = new EthernetAddr();
            this.type	 = new byte[2];
        }

        public void setSrcAddr(byte[] srcAddr) {
            System.arraycopy(srcAddr, 0, this.srcAddr.addr , 0, 6);
        }

        public void setDstAddr(byte[] dstAddr) {
            System.arraycopy(dstAddr, 0, this.dstAddr.addr , 0, 6);
        }
    }

    public int nUpperLayerCount = 0;

    public String pLayerName = null;

    public BaseLayer p_UnderLayer = null;

    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();

    public EthernetFrame m_Ethernet_Header = new EthernetFrame();

    public ARPLayer arpLayer;

    public FileAppLayer fileAppLayer;

    public ChatAppLayer chatAppLayer;

    public EthernetLayer(String name) {
        pLayerName = name;
    }

    public void setSrcAddr(byte[] addr){
        this.m_Ethernet_Header.setSrcAddr(addr);
    }

    public void setDstAddr(byte[] addr){
        this.m_Ethernet_Header.setDstAddr(addr);
    }

    public void setFileAppLayer(FileAppLayer fileAppLayer) {
        this.fileAppLayer = fileAppLayer;
    }

    public void setArpLayer(ARPLayer arpLayer) {
        this.arpLayer = arpLayer;
    }

    public void setChatAppLayer(ChatAppLayer chatAppLayer) {
        this.chatAppLayer = chatAppLayer;
    }

    public synchronized boolean Receive(byte[] input) {

        int frameType = byte2ToInt(input[12], input[13]);

        if ((isRightPacket(input) == false) || isRightAddress(input) == false) {
            return false;
        }

        if (frameType == 0x0806){
            input = removeAddressHeader(input, input.length);
            // ARP Layer로 전송
            GetUpperLayer(1).Receive(input);
            return true;
        }
        else if (frameType == 0x0800){
            input = removeAddressHeader(input, input.length);
            // IP 프로토콜의 프레임의 경우 IP Layer로 전송
            GetUpperLayer(0).Receive(input);
            return true;
        }

        return false;
    }

    public boolean Send(byte[] input, int length) {

        int opcode = byte2ToInt(input[6], input[7]);

        byte[] temp = null;

        if (opcode == 1) {
            // opcode가 1인 경우 ARP 요청 => 브로드캐스팅이므로, 목적지 주소를 전부 -1로 셋팅
            temp = addressing(input, input.length,
                    m_Ethernet_Header.srcAddr.addr,
                    new byte[]{ -1, -1, -1, -1, -1, -1 },
                    new byte[]{ 0x08, 0x06 });
        }
        else if(opcode == 2) {
            // opcode가 2인 경우 => ARP 응답에 해당
            temp = addressing(input, input.length,
                    new byte[]{ input[ 8], input[ 9], input[10], input[11], input[12], input[13] },
                    new byte[]{ input[18], input[19], input[20], input[21], input[22], input[23] },
                    new byte[]{ 0x08, 0x06 });
        }
        else {
            // 그 이외의 경우 프로토콜 타입을 IP로 바꿔 IP 레이어로 바로 올라가게 한다
            byte destIP[] = new byte[]{ input[16], input[17], input[18], input[19]};
            ARPLayer.ARPCache getCache = ARPLayer.ARPCacheTable.getCache(destIP);
            ARPLayer.Proxy getProxyCache = ARPLayer.ProxyARPEntry.get(destIP);
            boolean isArpRequest = false;
            byte[] emptyMac = new byte[6];

            if(getCache == null && getProxyCache == null){
                arpLayer.Send(new byte[10], 10);
                getCache = ARPLayer.ARPCacheTable.getCache(destIP);
            }

            while(getProxyCache == null && !getCache.Status()){

                getCache = ARPLayer.ARPCacheTable.getCache(destIP);
                getProxyCache = ARPLayer.ProxyARPEntry.get(destIP);
            }

            fileAppLayer.setStart(true);
            chatAppLayer.setStart(true);

            getCache = ARPLayer.ARPCacheTable.getCache(destIP);
            byte[] destMac = getCache != null ? getCache.getMacAddress() : getProxyCache.MacAddress();
            temp = addressing(input, input.length,
                    this.m_Ethernet_Header.srcAddr.addr,
                    destMac,
                    new byte[]{ 0x08, 0x00 });
        }

        if (p_UnderLayer.Send(temp, length + 14) == false) {
            return false;
        }

        return true;
    }

    private byte[] removeAddressHeader(byte[] input, int length) {

        byte[] temp = new byte[length - 14];

        for (int i = 0; i < length - 14; i++) {
            temp[i] = input[i + 14];
        }

        return temp;
    }

    private byte[] addressing(byte[] input, int length, byte[] src_address, byte[] dst_address, byte[] protocol) {

        byte[] buf = new byte[length + 14];

        buf[0]  = dst_address[0];
        buf[1]  = dst_address[1];
        buf[2]  = dst_address[2];
        buf[3]  = dst_address[3];
        buf[4]  = dst_address[4];
        buf[5]  = dst_address[5];

        buf[6]  = src_address[0];
        buf[7]  = src_address[1];
        buf[8]  = src_address[2];
        buf[9]  = src_address[3];
        buf[10] = src_address[4];
        buf[11] = src_address[5];

        buf[12] =    protocol[0];
        buf[13] =    protocol[1];

        for (int i = 0; i < length; i++)
            buf[14 + i] = input[i];

        return buf;
    }

    private boolean isRightPacket(byte[] input) {
        int  frameType = byte2ToInt(input[12]    , input[13]);

        if (frameType != 0x0800 && frameType != 0x0806) {
            return false;
        }
        return true;
    }

    private boolean isRightAddress(byte[] input) {

        int ffCount = 0;
        int fitCount = 0;
        int loopbackCnt = 0;

        for (int i = 0; i < 6; i++){
            if(input[i + 6] == m_Ethernet_Header.srcAddr.addr[i]){
                loopbackCnt++;
            }
        }

        if(loopbackCnt == 6) return false;

        for (int i = 0; i < 6; i++) {

            if (input[i] == -1) ffCount++;

            if (input[i] == m_Ethernet_Header.srcAddr.addr[i]) fitCount++;

        }

        if (ffCount == 6 || fitCount == 6)
            return true;

        return false;
    }

    private static int byte2ToInt(byte big_byte, byte little_byte) {

        int little_int = (int) little_byte;
        int big_int = (int) big_byte;

        if (little_int < 0) {
            little_int += 256;
        }

        return (little_int + (big_int << 8));
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
        this.p_UnderLayer = pUnderLayer;
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
}
