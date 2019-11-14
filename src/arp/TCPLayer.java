package arp;

import java.util.ArrayList;

public class TCPLayer implements BaseLayer {

    public int nUpperLayerCount = 0;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    public IPLayer ipLayer;
    final static int CHAT_MAX_LENGTH = 1456;
    final static int FAPP_MAX_LENGTH = 1448;

    _TCP_HEADER m_tHeader = new _TCP_HEADER();
    int HeaderSize = 20;

    private class _TCP_HEADER {
        byte[] srcPort;
        byte[] destPort;
        byte[] sequenceNumber;
        byte[] ackNumber;
        byte[] conditions;
        byte[] windowSize;
        byte[] checkSum;
        byte[] urgentPointer;

        public _TCP_HEADER() {
            this.srcPort = new byte[2];
            this.destPort = new byte[2];
            this.sequenceNumber = new byte[4];
            this.ackNumber = new byte[4];
            this.conditions = new byte[2];
            this.windowSize = new byte[2];
            this.checkSum = new byte[2];
            this.urgentPointer = new byte[2];
        }
    }

    public TCPLayer(String pName){
        pLayerName = pName;
        ResetHeader();

    }

    public void ResetHeader() {

    }

    public void setIpLayer(IPLayer ipLayer) {
        this.ipLayer = ipLayer;
    }

    void copyHeader(byte[] buffer){
        int beforeHeaderSize = 0;

        System.arraycopy(this.m_tHeader.srcPort, 0, buffer, beforeHeaderSize, this.m_tHeader.srcPort.length);
        beforeHeaderSize += this.m_tHeader.srcPort.length;

        System.arraycopy(this.m_tHeader.destPort, 0, buffer, beforeHeaderSize, this.m_tHeader.destPort.length);
        beforeHeaderSize += this.m_tHeader.destPort.length;

        System.arraycopy(this.m_tHeader.sequenceNumber, 0, buffer, beforeHeaderSize, this.m_tHeader.sequenceNumber.length);
        beforeHeaderSize += this.m_tHeader.sequenceNumber.length;

        System.arraycopy(this.m_tHeader.ackNumber, 0, buffer, beforeHeaderSize, this.m_tHeader.ackNumber.length);
        beforeHeaderSize += this.m_tHeader.ackNumber.length;

        System.arraycopy(this.m_tHeader.conditions, 0, buffer, beforeHeaderSize, this.m_tHeader.conditions.length);
        beforeHeaderSize += this.m_tHeader.conditions.length;

        System.arraycopy(this.m_tHeader.windowSize, 0, buffer, beforeHeaderSize, this.m_tHeader.windowSize.length);
        beforeHeaderSize += this.m_tHeader.windowSize.length;

        System.arraycopy(this.m_tHeader.checkSum, 0, buffer, beforeHeaderSize, this.m_tHeader.checkSum.length);
        beforeHeaderSize += this.m_tHeader.checkSum.length;

        System.arraycopy(this.m_tHeader.urgentPointer, 0, buffer, beforeHeaderSize, this.m_tHeader.urgentPointer.length);
        beforeHeaderSize += this.m_tHeader.urgentPointer.length;
    }

    public byte[] ObjToByte(byte[] input, int length){
        byte[] buffer = new byte[length + HeaderSize];

        copyHeader(buffer);

        for (int i = 0; i < length; i++){
            buffer[i + HeaderSize] = input[i];
        }

        return buffer;
    }

    public int getType(byte[] buffer) {
        return ((((int) buffer[0] & 0xff) << 8) |
                (((int) buffer[1] & 0xff) << 0));
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

    public boolean ARPSend(byte[] input, int length){
        byte[] buffer = ObjToByte(input, input.length);
        return ipLayer.ARPSend(buffer, 0);
    }

    @Override
    public synchronized boolean Send(byte[] input, int length) {
        byte[] buffer = ObjToByte(input, input.length);

        if(length == 0){
            return this.GetUnderLayer().Send(buffer, buffer.length);
        }

        if(length < 0){
            buffer[0] = (byte) (FAPP_MAX_LENGTH >> 8);
            buffer[1] = (byte) (FAPP_MAX_LENGTH);

            try{
                Thread.sleep(5);
            }catch (Exception e){
                System.out.println(e.getMessage());
            }

            return this.GetUnderLayer().Send(buffer, buffer.length);
        }else{
            buffer[0] = (byte) (CHAT_MAX_LENGTH >> 8);
            buffer[1] = (byte) (CHAT_MAX_LENGTH);
            return this.GetUnderLayer().Send(buffer, buffer.length);
        }
    }

    @Override
    public synchronized boolean Receive(byte[] input) {
        int type = getType(input);

        if(type == CHAT_MAX_LENGTH){
            this.GetUpperLayer(0).Receive(removeHeader(input));
        }else if(type == FAPP_MAX_LENGTH){
            this.GetUpperLayer(1).Receive(removeHeader(input));
        }
        return false;
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
