//import com.sun.org.apache.xpath.internal.operations.Bool;

import java.io.*;
import java.lang.reflect.Array;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.util.*;

public class TCPSocketImpl extends TCPSocket {

    protected EnhancedDatagramSocket socket;
    public enum State {
        SLOW_START, CONGESTION_AVOIDANCE, FAST_RECOVERY
    }
    private int SSThreshold = 42;
    private float windowSize = 1;
    private int seqNum;
    private int expectedSeqNum;
    private InetAddress dstIP;
    private InetAddress receiverDstIP;
    private int receiverDstPort;
    private int dstPort;
    private int srcPort;
    private State CCState = State.SLOW_START;

    private Integer recBufferSize = 100;

    public TCPSocketImpl(String ip, int port) throws Exception {
        //TODO: check seqNums and ackNums;
        super(ip, port);
        srcPort = Math.abs(new Random().nextInt(65536));
        System.out.println("PORT " + srcPort);
        seqNum = Math.abs(new Random().nextInt());
        socket = new EnhancedDatagramSocket(srcPort);
        InetAddress IPAddress = InetAddress.getByName(ip);
        dstIP = IPAddress;
        dstPort = (short) port;
        byte[] tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort, seqNum, -1, 1, 0, 0).pack();
        DatagramPacket packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;

        byte[] receiveData = new byte[20];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        TCPPacket r = new TCPPacket(receiveData);


        tcpPacket = new TCPPacket(new byte[0], srcPort, dstPort, seqNum, r.getSeqNum() + 1, 0, 0, 0).pack();
        packet = new DatagramPacket(tcpPacket, tcpPacket.length, IPAddress, port);
        socket.send(packet);
        seqNum++;

        onWindowChange();
    }

    public TCPSocketImpl(EnhancedDatagramSocket socket, int seqNum, int expectedSeqNum) throws Exception {
        super(socket);
        this.socket = socket;
        this.seqNum = seqNum;
        this.expectedSeqNum = expectedSeqNum;
    }

    @Override
    public void send(String pathToFile) throws Exception {
        socket.setSoTimeout(1000);
        HashMap<Integer, Integer> seqNumToChunk = new HashMap<>();
        int base = 0;
        int nextToSend = 0;
        boolean dupAck = false;
        int lastAckNum = -1;
        int dupAckCount = 0;
        int highWater = 0;
        int flightSize = 0;
        float prevWindowSize = windowSize;
        ArrayList<byte[]> chunks = new ArrayList<>();


        FileInputStream file = new FileInputStream(pathToFile);
        while(true){
            byte[] chunk = new byte[socket.getPayloadLimitInBytes() - 20];
            if(file.read(chunk) < 0)
                break;
            chunks.add(chunk);
        }
        file.close();

        while (nextToSend < chunks.size()) {

            System.out.println("Base: " + base);
            System.out.println("Window Size: " + windowSize);
            System.out.println("NextToSend: " + nextToSend);
            int lastAckChunk = 0;
            if (seqNumToChunk.containsKey(lastAckNum))
                lastAckChunk = seqNumToChunk.get(lastAckNum);
            for (int i = nextToSend; i < base + windowSize; i++) {
//                if (CCState == State.FAST_RECOVERY) {
//                    if (flightSize >= windowSize) break;
//                } else {
//                    if (nextToSend - 1 - lastAckChunk >= windowSize) break;
//                }
                byte[] chunkToSend = (byte[]) chunks.get(nextToSend);
                seqNumToChunk.put(seqNum, nextToSend);
                TCPPacket packet = new TCPPacket(chunkToSend, srcPort, dstPort, seqNum,
                        -1, 0, 0, chunkToSend.length);
                if(nextToSend == chunks.size()-1)
                    packet.setLastPacket();
                byte[] packetByte = packet.pack();
                DatagramPacket dp = new DatagramPacket(packetByte, packetByte.length, dstIP, dstPort);
                socket.send(dp);
                flightSize++;
                nextToSend += 1;
                seqNum += chunkToSend.length;
                System.out.println("###########################");
                System.out.println("Base: " + base);
                System.out.println("Next To Send: " + nextToSend);
                System.out.println("Seq. Num.: " + seqNum);
                System.out.println("---------------------------");
                if (nextToSend >= chunks.size())
                    break;
            }

            boolean shouldRetransmit = false;
            try {
                byte[] receiveData = new byte[20];
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                TCPPacket r = new TCPPacket(receiveData);
                System.out.println(r.toString());
                State nextState = CCState;
                int rwnd = r.getWinSize();
                System.out.println("Last Ack Num: " + lastAckNum);
                flightSize -= seqNumToChunk.get(r.getAckNum()) - lastAckChunk;

                if (lastAckNum == r.getAckNum()) {
                    dupAck = true;
                    dupAckCount++;
                    System.out.println("DupAckCount: " + dupAckCount);
                    if (dupAckCount == 3 && CCState != State.FAST_RECOVERY) {
                        System.out.println("Triple DupAck!");
                        shouldRetransmit = true;
                        SSThreshold = Math.max((int)(windowSize / 2), 2);
                        windowSize = SSThreshold + 3;
                        onWindowChange();
                        flightSize -= 1;
                        highWater = nextToSend - 1;
                        nextState = CCState.FAST_RECOVERY;
                        dupAckCount = 0;
                    }
                } else {
                    dupAckCount = 0;
                }


                switch (CCState) {
                    case SLOW_START:
                        if (!dupAck) {
                            windowSize++;
                            onWindowChange();
                        }
                        if (windowSize >= SSThreshold) {
                            prevWindowSize = windowSize;
                            nextState = State.CONGESTION_AVOIDANCE;
                        }
                        break;
                    case CONGESTION_AVOIDANCE:
                        if (!dupAck) {
                            windowSize += 1 / prevWindowSize;
                            onWindowChange();
                        }
                        break;
                    case FAST_RECOVERY:
                        if (!dupAck) {
                            if (seqNumToChunk.get(lastAckNum) < highWater) {
                                windowSize -= seqNumToChunk.get(r.getAckNum()) - seqNumToChunk.get(lastAckNum) + 1;
                                windowSize = windowSize > 0 ? windowSize : 0;
                                shouldRetransmit = true;
                            } else {
                                windowSize = SSThreshold;
                                prevWindowSize = windowSize;
                                nextState = CCState.CONGESTION_AVOIDANCE;
                            }
                            onWindowChange();
                        }
                        break;
                }
                windowSize = Math.min(windowSize, rwnd);
                lastAckNum = r.getAckNum() > lastAckNum ? r.getAckNum() : lastAckNum;
                CCState = nextState;

            } catch (SocketTimeoutException ste) {
                nextToSend = base;
                SSThreshold = Math.max((int) windowSize / 2, 2);
                windowSize = 1;
                dupAckCount = 0;
                onWindowChange();
                shouldRetransmit = true;
            }

            if (shouldRetransmit) {
                int retransmitChunkNumber = seqNumToChunk.get(lastAckNum) + 1;
                byte[] chunkToSend = chunks.get(retransmitChunkNumber);
                byte[] packet = new TCPPacket(chunkToSend, srcPort, dstPort,
                        lastAckNum + chunkToSend.length,
                        -1, 0, 0, chunkToSend.length).pack();
                int seq = lastAckNum + chunkToSend.length;
                System.out.println("Retransmitted " + seq);
                DatagramPacket dp = new DatagramPacket(packet, packet.length, dstIP, dstPort);
                socket.send(dp);
                flightSize += 1;
            } else {
                base++;
            }



            System.out.println("SSThreshold: " + SSThreshold);
            System.out.println("Window Size: " + windowSize);
            System.out.println(CCState);
            System.out.println("###########################");
        }
    }

    private TCPPacket receivePacket() throws Exception {
        byte[] receiveData = new byte[socket.getPayloadLimitInBytes()];
        DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
        socket.receive(receivePacket);
        receiverDstIP = receivePacket.getAddress();
        receiverDstPort = receivePacket.getPort();
        System.out.println(receiverDstIP.toString());
        System.out.println(receiverDstPort);
        return new TCPPacket(receiveData);
    }

    private Integer sortAndFindInOrderItemCount(ArrayList<TCPPacket> list) {
        list.sort(Comparator.comparing(TCPPacket::getSeqNum));
        Integer count = 0;
        Integer seqNumber;
        seqNumber = this.expectedSeqNum;
        if(list.isEmpty())
            return 0;
//        seqNumber = list.get(0).getSeqNum();
//        for (TCPPacket packet :
//                list) {
//            System.out.println(packet.getSeqNum());
//        }
        for (TCPPacket item : list) {
            if(item.getSeqNum() == seqNumber) {
                count++;
                seqNumber += item.getDataLen();
            }
            else
                break;
        }
        return count;
    }

    public void writeToFile(ArrayList<TCPPacket> list, String pathToFile) throws IOException {
        new File(pathToFile).delete();
        try (FileOutputStream fileStream = new FileOutputStream(pathToFile)) {
            for (TCPPacket item : list) {
                fileStream.write(item.getData());
            }
        }
    }

    @Override
    public void receive(String pathToFile) throws Exception {
        //TODO: rwnd;
        ArrayList<TCPPacket> allPackets = new ArrayList<>();
        ArrayList<TCPPacket> receiveBuffer = new ArrayList<>();
        ArrayList<Integer> receivedSequenceNumbers = new ArrayList<>();
        Integer lastAckNumber = -1;
        Boolean lastPacket = Boolean.FALSE;

        while (true) {
            TCPPacket receivedPacket = receivePacket();
            Integer currentSeqNumber = receivedPacket.getSeqNum();
            if (!receivedSequenceNumbers.contains(currentSeqNumber)) {
                receiveBuffer.add(receivedPacket);
                System.out.println("LEN");
                receivedSequenceNumbers.add(receivedPacket.getSeqNum());
                System.out.println(receiveBuffer.size());
                System.out.println(receivedSequenceNumbers.size());
            }
            Integer inOrderBufferedItemsCount = sortAndFindInOrderItemCount(receiveBuffer);
            System.out.println("Inorder count: " + inOrderBufferedItemsCount);

            System.out.println("$$$$$$$$$$$$$$$$$$$$$$$$$$$");
            System.out.println("Packet Received:");
            System.out.println("Expected Seq. numebr:" + expectedSeqNum);
            System.out.println("Packet Seq. Num.: " + receivedPacket.getSeqNum());
            System.out.println("---------------------------");

            if (inOrderBufferedItemsCount > 0) {
                lastAckNumber = receiveBuffer.get(inOrderBufferedItemsCount - 1).getSeqNum();
                ArrayList<TCPPacket> subBuffer = new ArrayList<>(receiveBuffer.subList(0, inOrderBufferedItemsCount));
                receiveBuffer.removeAll(subBuffer);
                allPackets.addAll(subBuffer);
                expectedSeqNum += allPackets.get(allPackets.size() - 1).getDataLen();
            }
            //send ACK
            //check this!!!
//            TCPPacket lastReceived = new TCPPacket(allPackets.get(allPackets.size() - 1).pack());
//            lastReceived.setAckNum(lastAckNumber);
//            lastReceived.setWinSize(recBufferSize - receiveBuffer.size());
            TCPPacket ackPacket = new TCPPacket(new byte[0], srcPort, dstPort, 0, lastAckNumber, 0,
                    recBufferSize - receiveBuffer.size(), 0);
            byte[] ackTcpPacket = (ackPacket).pack();
            DatagramPacket ackDatagramPacket = new DatagramPacket(ackTcpPacket, ackTcpPacket.length,
                    receiverDstIP, receiverDstPort);
            socket.send(ackDatagramPacket);

            System.out.println("###########################");
            System.out.println("ACK Sent:");
            System.out.println("ACK Number is:" + lastAckNumber);
            System.out.println("Window Size is:" + (recBufferSize - receiveBuffer.size()));
            System.out.println("###########################");

            //check for last packet

            for (TCPPacket item : allPackets) {
                if (item.isLastPacket()) {
                    lastPacket = Boolean.TRUE;
                    break;
                }
            }
            if (lastPacket) {
                System.out.print(allPackets.size() + " Packets Received!");
                break;
            }
        }
        allPackets.sort(Comparator.comparing(TCPPacket::getSeqNum));
        //write to file
        writeToFile(allPackets, pathToFile);
    }
    @Override
    public void close() throws Exception {
        socket.close();
    }

    @Override
    public long getSSThreshold() {
        return SSThreshold;
    }

    @Override
    public long getWindowSize() {
        return (long) windowSize;
    }
}
