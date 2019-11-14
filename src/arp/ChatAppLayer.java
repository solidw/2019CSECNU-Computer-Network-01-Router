package arp;

import java.util.ArrayList;

public class ChatAppLayer implements BaseLayer {
	final static int MAX_LENGTH = 1456;
	public int nUpperLayerCount = 0;
	public String pLayerName = null;
	public BaseLayer p_UnderLayer = null;
	public ArrayList<BaseLayer> p_aUpperLayer = new ArrayList<BaseLayer>();
	byte byteBuffer[] = new byte[(int)Math.pow(2, 20)];
	int bufferInputCount = 0;
	boolean isStart = false;

	private class _CAPP_HEADER {
		byte capp_type;
		byte capp_unused;
		byte[] capp_totlen;
		byte[] capp_data;

		public _CAPP_HEADER() {
			this.capp_type = 0x00;
			this.capp_unused = 0x00;
			this.capp_totlen = new byte[2];
			this.capp_data = null;
		}
	}

	_CAPP_HEADER m_sHeader = new _CAPP_HEADER();

	public void setStart(boolean start) {
		isStart = start;
	}

	public ChatAppLayer(String pName) {
		// super(pName);
		// TODO Auto-generated constructor stub
		pLayerName = pName;
		ResetHeader();
	}

	// 헤더의 데이터들을 초기화해준다.
	public void ResetHeader() {
		for (int i = 0; i < 2; i++) {
			m_sHeader.capp_totlen[i] = (byte) 0x00;
		}
		m_sHeader.capp_data = null;
	}

	// 데이터를 가공한다.
	public byte[] ObjToByte(_CAPP_HEADER Header, byte[] input, int length) {
		byte[] buf = new byte[length + 4];

		// 길이정보와 type 정보 등을 추가한다.
		buf[0] = (byte) (length % 256);
		buf[1] = (byte) (length / 256);
		buf[2] = 0x02;
		buf[3] = m_sHeader.capp_unused;

		// 받은 데이터를 추가한다.
		for (int i = 0; i < length; i++)
			buf[4+ i] = input[i];

		return buf;
	}

	public synchronized  void notifySend(){
		notifyAll();
	}

	public synchronized boolean Send(byte[] input, int length) {

		isStart = false;

		// 데이터를 추가하여 가공한다.
		int sendLoopCount = input.length / MAX_LENGTH;
		int remainDataLength = input.length % MAX_LENGTH;
		byte newData[];

		for(int i = 0; i < sendLoopCount; i++){
			newData = new byte[MAX_LENGTH];
			for(int index = 0; index < MAX_LENGTH; index++){
				newData[index] = input[index + (i * MAX_LENGTH)];
			}
			byte[] sendData = ObjToByte(m_sHeader, newData, newData.length);
			sendData[2] = (byte)(i+1);
			this.GetUnderLayer().Send(sendData, 0);
			while(!isStart){}
			this.GetUnderLayer().Send(sendData, sendData.length);
		}

		if(remainDataLength > 0){
			newData = new byte[remainDataLength];
			for(int i = 0; i < remainDataLength; i++){
				newData[i] = input[i + (sendLoopCount * MAX_LENGTH)];
			}
			byte[] sendData = ObjToByte(m_sHeader, newData, newData.length);
			if(sendLoopCount > 0){
				sendData[2] = -1;
			}else{
				sendData[2] = 0;
			}
			this.GetUnderLayer().Send(sendData, 0);
			while(!isStart){}
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.GetUnderLayer().Send(sendData, sendData.length);
		}

		isStart = false;
		return true;
	}

	// 추가한 데이터들을 삭제한다.
	public byte[] removeChatHeader(byte[] input, int length) {

		byte[] buf = new byte[length];

		// index 4까지는 내가 추가한 데이터이니 삭제해준다.
		for(int i = 4; i < length + 4; i++) {
			buf[i - 4] = input[i];
		}
		return buf;
	}

	public synchronized boolean Receive(byte[] input) {
		byte[] data;
		int remain = input[0];
		if(remain < 0){
			remain += 256;
		}
		int dataLength = remain + (256 * input[1]);

		if(input[2] == 0x00){
			// 헤더 정보를 이용하여 길이 정보를 받는다.
			data = removeChatHeader(input, dataLength);
			// 상위 레이어에 데이터를 전송시킨다.
			this.GetUpperLayer(0).Receive(data);
			return true;

		}else if(input[2] >= 1){
			int headerType = input[2];
			data = removeChatHeader(input, dataLength);
			for(int i = 0; i < dataLength; i++){
				byteBuffer[i + (MAX_LENGTH * (headerType - 1))] = data[i];
			}
			bufferInputCount ++;
			return true;

		}else{
			byte[] totalData;
			int receiveBufferLength;

			data = removeChatHeader(input, dataLength);

			receiveBufferLength = (MAX_LENGTH * bufferInputCount) + dataLength;
			totalData = new byte[receiveBufferLength];

			for(int i = 0; i < dataLength; i++){
				byteBuffer[i + (bufferInputCount * MAX_LENGTH)] = data[i];
			}

			System.arraycopy(byteBuffer, 0, totalData, 0, receiveBufferLength);
			this.GetUpperLayer(0).Receive(totalData);

			bufferInputCount = 0;
			return true;
		}
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
