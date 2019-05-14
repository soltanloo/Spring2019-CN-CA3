import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;

public class TCPPacket {

    private byte[] data;
    private int srcPort;
    private int dstPort;
    private int seqNum;
    private int ackNum;
    private int SYN;
    private int ACK;
    private int winSize;
    private int dataLen;

    public TCPPacket(byte[] data, int srcPort, int dstPort, int seqNum, int ackNum, int SYN,
                     int winSize, int dataLen) {
        this.data = data;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.seqNum = seqNum;
        this.ackNum = ackNum;
        this.ACK = ackNum == -1 ? 0 : 1;
        this.SYN = SYN;
        this.winSize = winSize;
        this.dataLen = dataLen;
    }

    public TCPPacket(byte[] packet) {
        short dataLength = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(18);
        byte[] d = new byte[dataLength];
        for(int i=0; i<dataLength; i++)
            d[i] = packet[i+20];
        this.data = d;
        this.srcPort = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(0);
        this.dstPort = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(2);
        this.seqNum = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        this.ackNum = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getInt(8);
        this.SYN = (int)packet[13];
        this.ACK = (int)packet[12];
        this.winSize = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(14);
        this.dataLen = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN).getShort(18);
    }

    public byte[] pack() {
        byte[] header = new byte[20];
        addHeader(header, srcPort, dstPort, seqNum, ackNum, SYN, winSize, dataLen);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
        try {
            outputStream.write( header );
            outputStream.write( data );
        } catch (IOException e) {
            e.printStackTrace();
        }
        return outputStream.toByteArray( );
    }

    static public void addHeader(byte[] ba, int sp, int dp, int sn, int ack, int syn, int ws, int dl){

        for(int i=0; i<20; i++){
            ba[i] = (byte)0;
        }

        //Little Endian convention
        //add source_port and destination_port
        short temp = (short)sp;
        ba[0] = (byte)(temp & 0xff);
        ba[1] = (byte)((temp >> 8) & 0xff);

        temp = (short)dp;
        ba[2] = (byte)(temp & 0xff);
        ba[3] = (byte)((temp >> 8) & 0xff);

        //add sequence number
        ba[4] = (byte)(sn & 0xff);
        ba[5] = (byte)((sn >> 8) & 0xff);
        ba[6] = (byte)((sn >> 16) & 0xff);
        ba[7] = (byte)((sn >> 24) & 0xff);

        //if ack_num != -1, add ack_num and ack_sig
        if(ack != -1){
            ba[8] = (byte)(ack & 0xff);
            ba[9] = (byte)((ack >> 8) & 0xff);
            ba[10] = (byte)((ack >> 16) & 0xff);
            ba[11] = (byte)((ack >> 24) & 0xff);
            ba[12] = (byte)1;
        }

        //if syn=1; set syn = 1
        if(syn == 1)
            ba[13] = (byte)1;

        //add window size
        temp = (short)ws;
        ba[14] = (byte)(temp & 0xff);
        ba[15] = (byte)((temp >> 8) & 0xff);

        //add checksum
        temp = (short)0;
//        for(int i=0; i<dl; i++){
//            temp += (short)ba[i+20];
//        }
        ba[16] = (byte)(temp & 0xff);
        ba[17] = (byte)((temp >> 8) & 0xff);

        temp = (short)dl;
        //add data_length
        ba[18] = (byte)(temp & 0xff);
        ba[19] = (byte)((temp >> 8) & 0xff);

    }

    //logfile format
    public String toString(){
        String s = "";

        s += new Date().toString();
        s += ", src: " + getSrcPort();
        s += ", dest: " + getDstPort();
        s += ", seq: " + getSeqNum();
        s += ", ACK: " + getAckNum();
        s += ", FLAGS: " + "ACK="+getACK() +" SYN="+getSYN();

        return s;
    }

    static public byte[] parseByte(byte[] bArray, int size, int seq, int offset){

        if(seq >= bArray.length)
            return null;

        int length;
        byte[] newArray = null;

        if(seq + size < bArray.length)
            length = size+offset;
        else
            length = bArray.length - seq + offset;

        int counter = seq;
        newArray = new byte[length];

        for(int i=offset; i<length; i++)
            newArray[i] = bArray[counter++];

        return newArray;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(int srcPort) {
        this.srcPort = srcPort;
    }

    public int getDstPort() {
        return dstPort;
    }

    public void setDstPort(int dstPort) {
        this.dstPort = dstPort;
    }

    public int getSeqNum() {
        return seqNum;
    }

    public void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }

    public int getAckNum() {
        return ackNum;
    }

    public void setAckNum(int ackNum) {
        this.ackNum = ackNum;
    }

    public int getSYN() {
        return SYN;
    }

    public void setSYN(int SYN) {
        this.SYN = SYN;
    }

    public int getACK() {
        return ACK;
    }

    public void setACK(int ACK) {
        this.ACK = ACK;
    }

    public int getWinSize() {
        return winSize;
    }

    public void setWinSize(int winSize) {
        this.winSize = winSize;
    }

    public int getDataLen() {
        return dataLen;
    }

    public void setDataLen(int dataLen) {
        this.dataLen = dataLen;
    }
}
