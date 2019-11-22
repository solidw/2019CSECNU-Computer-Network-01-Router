package arp;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class FileAppLayer implements BaseLayer {
    public int nUpperLayerCount = 0;
    final static int MAX_LENGTH = 1448;
    public String pLayerName = null;
    public BaseLayer p_UnderLayer = null;
    public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
    byte byteBuffer[] = new byte[(int) Math.pow(2, 28)];
    int latestSequenceNumber = -1;
    String fileName;
    int receiveByteLength;
    int inputCount = 0;
    boolean isStart = false;

    private class _FAPP_HEADER {
        byte[] fapp_totlen;
        byte[] fapp_type;
        byte fapp_msg_type;
        byte ed;
        byte[] fapp_seq_num;
        byte[] fapp_data;

        public _FAPP_HEADER() {
            this.fapp_totlen = new byte[4];
            this.fapp_type = new byte[2];
            this.fapp_msg_type = 0x00;
            this.ed = 0x00;
            this.fapp_seq_num = new byte[4];
            this.fapp_data = null;
        }
    }

    _FAPP_HEADER m_sHeader = new _FAPP_HEADER();


    public void setStart(boolean start) {
        isStart = start;
    }

    public FileAppLayer(String pName) {
        // super(pName);
        // TODO Auto-generated constructor stub
        pLayerName = pName;
        ResetHeader();
    }

    // ?????? ?????????? ???????????.
    public void ResetHeader() {
        for (int i = 0; i < 4; i++) {
            m_sHeader.fapp_totlen[i] = (byte) 0x00;
            m_sHeader.fapp_type[i % 2] = (byte) 0x00;
            m_sHeader.fapp_seq_num[i] = (byte) 0x00;
        }
        m_sHeader.fapp_data = null;
    }

    public int ByteToInt(byte[] buffer, int startPoint) {
        return ((((int) buffer[startPoint] & 0xff) << 24) |
                (((int) buffer[startPoint + 1] & 0xff) << 16) |
                (((int) buffer[startPoint + 2] & 0xff) << 8) |
                (((int) buffer[startPoint + 3] & 0xff)));
    }

    public byte[] ObjToByte(_FAPP_HEADER Header, byte[] input, int length) {
        byte[] buf = new byte[length + 12];

        buf[0] = (byte) (length >> 24);
        buf[1] = (byte) (length >> 16);
        buf[2] = (byte) (length >> 8);
        buf[3] = (byte) (length);
        buf[4] = 0x00;
        buf[5] = 0x00;
        buf[6] = 0x00;
        buf[7] = 0x00;


        for (int i = 0; i < length; i++)
            buf[12 + i] = input[i];

        return buf;
    }


    public byte[] removeFappHeader(byte[] input, int length) {
        byte[] buf = new byte[length];

        for (int i = 12; i < length + 12; i++) {
            buf[i - 12] = input[i];
        }
        return buf;
    }

    public synchronized boolean Receive(byte[] input) {
        byte[] data;
        int dataLength = ByteToInt(input, 0);

        if (input[4] == 0) {
            data = removeFappHeader(input, dataLength);
            fileName = new String(data);
            receiveByteLength = ByteToInt(input, dataLength + 12);
            ((IPCDlg) GetUpperLayer(0)).setProgressValue(0);
        } else if (input[4] == 1) {
            int sequenceNumber = ByteToInt(input, 8);
            data = removeFappHeader(input, dataLength);


            for (int i = 0; i < dataLength; i++) {
                byteBuffer[(i + (sequenceNumber * MAX_LENGTH))] =  data[i];
            }

            int percentage = (int) (((double) (sequenceNumber + 1) / (receiveByteLength / MAX_LENGTH)) * 100);
            ((IPCDlg) GetUpperLayer(0)).setProgressValue(percentage);

            inputCount++;
            latestSequenceNumber = sequenceNumber;
        }
        if (input[4] == 2) {
            try {
                byte splitByte[] = new byte[receiveByteLength];
                System.arraycopy(byteBuffer, 0, splitByte, 0 ,receiveByteLength);

                FileOutputStream stream = new FileOutputStream(fileName);
                stream.write(byteBuffer);
                stream.close();
                ((IPCDlg) GetUpperLayer(0)).setProgressValue(100);
                System.out.println("Finished");
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
        return true;
    }



    public synchronized boolean firstSend(File file, int inputByteLengh) {
        String inputFileName = file.getName();
        int sendDataLength;
        byte[] addedLengthData;
        byte[] fileNameBuffer = inputFileName.getBytes();
        byte[] sendData = ObjToByte(m_sHeader, fileNameBuffer, fileNameBuffer.length);
        sendData[4] = 0;
        sendDataLength = sendData.length;

        addedLengthData = new byte[sendData.length + 4];
        System.arraycopy(sendData, 0, addedLengthData, 0, sendData.length);
        addedLengthData[sendDataLength] = (byte) (inputByteLengh >> 24);
        addedLengthData[sendDataLength + 1] = (byte) (inputByteLengh >> 16);
        addedLengthData[sendDataLength + 2] = (byte) (inputByteLengh >> 8);
        addedLengthData[sendDataLength + 3] = (byte) (inputByteLengh);

        this.GetUnderLayer().Send(addedLengthData, addedLengthData.length * -1);

        ((IPCDlg) GetUpperLayer(0)).setProgressValue(0);

//        try{
//
//            return true;
//        }catch (InterruptedException e){
//            System.out.println(e.getMessage());
//        }

        return false;
    }

    @Override
    public synchronized boolean Send(String inputFilePath) {
        isStart = false;
        File file = new File(inputFilePath);
        byte[] inputByte = new byte[(int) file.length()];
        byte[] sendData;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            fileInputStream.read(inputByte);
        } catch (IOException e) {
            System.out.println(e.getMessage());
            return false;
        }

        firstSend(file, inputByte.length);

        while(!isStart){}

        try{
            Thread.sleep(10000);
        }catch (InterruptedException e){
            System.out.println(e.getMessage());
        }

        int sendLoopCount = inputByte.length / MAX_LENGTH;
        int remainDataLength = inputByte.length % MAX_LENGTH;
        byte newData[];
        int sequenceNumber;
        for (sequenceNumber = 0; sequenceNumber < sendLoopCount; sequenceNumber++) {
            newData = new byte[MAX_LENGTH];
            for (int index = 0; index < MAX_LENGTH; index++) {
                newData[index] = inputByte[index + (sequenceNumber * MAX_LENGTH)];
            }
            sendData = ObjToByte(m_sHeader, newData, newData.length);
            sendData[8] = (byte) (sequenceNumber >> 24);
            sendData[9] = (byte) (sequenceNumber >> 16);
            sendData[10] = (byte) (sequenceNumber >> 8);
            sendData[11] = (byte) (sequenceNumber);
            sendData[4] = 1;


            this.GetUnderLayer().Send(sendData, sendData.length * -1);


            if ((inputByte.length / MAX_LENGTH) != 0) {
                int percentage = (int) (((double) (sequenceNumber + 1) / (inputByte.length / MAX_LENGTH)) * 100);
                ((IPCDlg) GetUpperLayer(0)).setProgressValue(percentage);
            }
        }

        if (remainDataLength > 0) {
            newData = new byte[remainDataLength];
            for (int i = 0; i < remainDataLength; i++) {
                newData[i] = inputByte[i + (sendLoopCount * MAX_LENGTH)];
            }
            sendData = ObjToByte(m_sHeader, newData, newData.length);
            sendData[8] = (byte) (sequenceNumber >> 24);
            sendData[9] = (byte) (sequenceNumber >> 16);
            sendData[10] = (byte) (sequenceNumber >> 8);
            sendData[11] = (byte) (sequenceNumber);
            sendData[4] = 1;
            this.GetUnderLayer().Send(sendData, sendData.length * -1);
        }

        ((IPCDlg) GetUpperLayer(0)).setProgressValue(100);
        sendData = new byte[6];

        try{
            Thread.sleep(5);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }

        sendData[4] = 2;
        this.GetUnderLayer().Send(sendData, sendData.length * -1);

        isStart = false;
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
