import com.sun.xml.internal.ws.server.provider.ProviderInvokerTube;

import java.util.ArrayList;
import java.util.Arrays;

public class IPLayer implements BaseLayer {

    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ARPLayer arpLayer;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    private RoutingTable routingTable;

    _IP_HEADER m_iHeader = new _IP_HEADER();
    int HeaderSize = 20;
    EthernetLayer ethernetLayer;

    private class _IP_HEADER {
        byte versionAndHLength;
        byte serviceType;
        byte[] totalLength;
        byte[] identification;
        byte[] flag;
        byte ttl;
        byte protocol;
        byte[] headerChecksum;
        byte[] srcIP;
        byte[] destIP;

        public void setSrcIP(byte[] srcIP) {
            this.srcIP = srcIP;
        }

        public void setDestIP(byte[] destIP) {
            this.destIP = destIP;
        }

        public _IP_HEADER() {
            totalLength = new byte[2];
            identification = new byte[2];
            flag = new byte[2];
            headerChecksum = new byte[2];
            srcIP = new byte[4];
            destIP = new byte[4];
        }
    }

    public void setArpLayer(ARPLayer arpLayer) {
        this.arpLayer = arpLayer;
    }

    public void setSrcIP(byte[] srcIP) {
        this.m_iHeader.setSrcIP(srcIP);
    }

    public void setDestIP(byte[] destIP) {
        this.m_iHeader.setDestIP(destIP);
    }

    public void setEthernetLayer(EthernetLayer ethernetLayer) {
        this.ethernetLayer = ethernetLayer;
    }


    public IPLayer(String pName){
        pLayerName = pName;
        routingTable = RoutingTable.getInstance();
        ResetHeader();
    }

    public void ResetHeader() {
        this.m_iHeader.versionAndHLength = (0x04 << 4);
        this.m_iHeader.versionAndHLength += (byte) HeaderSize;
    }


    void copyHeader(byte[] buffer){
        int beforeHeaderSize = 0;

        buffer[beforeHeaderSize++] = this.m_iHeader.versionAndHLength;
        buffer[beforeHeaderSize++] = this.m_iHeader.serviceType;

        System.arraycopy(this.m_iHeader.totalLength, 0, buffer, beforeHeaderSize, this.m_iHeader.totalLength.length);
        beforeHeaderSize += this.m_iHeader.totalLength.length;

        System.arraycopy(this.m_iHeader.identification, 0, buffer, beforeHeaderSize, this.m_iHeader.identification.length);
        beforeHeaderSize += this.m_iHeader.identification.length;

        System.arraycopy(this.m_iHeader.flag, 0, buffer, beforeHeaderSize, this.m_iHeader.flag.length);
        beforeHeaderSize += this.m_iHeader.flag.length;

        buffer[beforeHeaderSize++] = this.m_iHeader.ttl;
        buffer[beforeHeaderSize++] = this.m_iHeader.protocol;

        System.arraycopy(this.m_iHeader.headerChecksum, 0, buffer, beforeHeaderSize, this.m_iHeader.headerChecksum.length);
        beforeHeaderSize += this.m_iHeader.headerChecksum.length;

        System.arraycopy(this.m_iHeader.srcIP, 0, buffer, beforeHeaderSize, this.m_iHeader.srcIP.length);
        beforeHeaderSize += this.m_iHeader.srcIP.length;


        System.arraycopy(this.m_iHeader.destIP, 0, buffer, beforeHeaderSize, this.m_iHeader.destIP.length);

    }

    void updateTotalLength(int totalLength){
        m_iHeader.totalLength[0] = (byte) (totalLength >> 8);
        m_iHeader.totalLength[1] = (byte) (totalLength);
    }

    public byte[] ObjToByte(byte[] input, int length){
        byte[] buffer = new byte[length + HeaderSize];

        updateTotalLength(length + HeaderSize);

        copyHeader(buffer);

        for (int i = 0; i < length; i++){
            buffer[i + HeaderSize] = input[i];
        }

        return buffer;
    }


    public byte[] removeHeader(byte[] input) {
        int inputLength = input.length;
        byte[] buf = new byte[inputLength - HeaderSize];

        // mac 주소와 type 등에 관한 정보를 삭제한다.
        for (int i = HeaderSize; i < inputLength; i++) {
            buf[i - HeaderSize] = input[i];
        }
        // 삭제한 저보를 반환한다.
        return buf;
    }

    public boolean ARPSend(byte[] input, int length) {
        byte[] buffer = ObjToByte(input, input.length);
        return arpLayer.Send(buffer, buffer.length);
    }

    @Override
    public synchronized boolean Send(byte[] input, int length) {
        byte[] buffer = ObjToByte(input, input.length);
        return ethernetLayer.Send(buffer, buffer.length);

    }

    @Override
    public synchronized boolean Receive(byte[] input) {

        int startOfSourceIp = 12;
        int startOfDestIp = 16;

        byte[] sourceIp = new byte[] {
                input[startOfSourceIp], input[startOfSourceIp + 1], input[startOfSourceIp + 2], input[startOfSourceIp + 3]};

        byte[] targetIp = new byte[]{
                input[startOfDestIp], input[startOfDestIp+ 1], input[startOfDestIp + 2], input[startOfDestIp + 3]};

        if(Arrays.equals(targetIp, this.m_iHeader.srcIP)){
            byte[] response = input;

            m_iHeader.setDestIP(sourceIp);

            response = removeHeader(input);

            response[0] = 0x00;

            ObjToByte(response, response.length);

            while(true){
                ARPLayer.ARPCache cache = ARPLayer.ARPCacheTable.getCache(sourceIp);
                if(cache != null) break;
            }

            if(p_UnderLayer.Send(response, response.length) == false) return false;
        }

        byte[] realDest = RoutingTable.getInstance().Route(targetIp);

        if (realDest == null) return false;

        input[16] = realDest[0];
        input[17] = realDest[1];
        input[18] = realDest[2];
        input[19] = realDest[3];

        while(true){
            ARPLayer.ARPCache cache = ARPLayer.ARPCacheTable.getCache(sourceIp);
            if(cache != null) break;
        }

        if(p_UnderLayer.Send(input, input.length) == false){
            return false;
        }

        return true;
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
    public void SetUpperUnderLayer(BaseLayer pUULayer) {
        this.SetUpperLayer(pUULayer);
        pUULayer.SetUnderLayer(this);
    }
}
