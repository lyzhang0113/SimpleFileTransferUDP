import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.TimeUnit;


public class SftpClient {

    /* Print DEBUG Messages */
    private static final boolean DEBUG = false;

    /* The name of the file to be sent to the server */
    private static final String FILENAME = "inputfile";

    /* Server Port */
    private static final int SERVER_PORT = 9093;

    /* Packet is retransmitted after this timeout */
    private static final int RETRANSMISSION_TIMEOUT = 1000; // milliseconds

    /* The maximum number of times that the client will attempt retransmission a packet on timeout */
    private static final int RETRANSMISSION_LIMIT = 5;

    /* The data size of each packet, except possibly the last one */
    private static final int MAX_DATA_SIZE_PER_PACKET = 512; // bytes

    /* File transfer always starts with this sequence number */
    private static final byte START_SEQ_NUM = (byte) 0;

    private static int packet_count = 0;

    public static void main(String[] args) throws Exception {

        // Get Command Line Arguments
        if (args.length != 1) {
            System.out.println("Incorrect Usage: \njava SftpClient <server-address>");
            return;
        }
        InetAddress serverAddress = InetAddress.getByName(args[0]);

        // Create UDP socket for sending file
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(RETRANSMISSION_TIMEOUT);

        // Load Input File
        URL inputFileUrl = SftpClient.class.getResource(FILENAME);
        if (inputFileUrl == null) throw new FileNotFoundException("ERROR: There is no file named [" + FILENAME + "] in current working directory!");
        FileInputStream inputStream = new FileInputStream(inputFileUrl.getPath());

        try {
            // Read Input File
            byte[] data = inputStream.readNBytes(MAX_DATA_SIZE_PER_PACKET);
            long startTime = System.nanoTime();
            byte seq = START_SEQ_NUM;

            while (data.length == MAX_DATA_SIZE_PER_PACKET) {
                sendDataPacket(socket, serverAddress, data, seq);
                // Set data to next packet
                data = inputStream.readNBytes(MAX_DATA_SIZE_PER_PACKET);
                seq ^= 1; // flip sequence number
            }

            // last data packet
            sendDataPacket(socket, serverAddress, data, seq);
            long diff = System.nanoTime() - startTime;
            System.out.println("sFTP: file sent successfully to " + serverAddress.getHostAddress()
                    + " in " + TimeUnit.SECONDS.convert(diff, TimeUnit.NANOSECONDS) + " secs");
        } catch (ConnectException connectException) {
            System.out.println("sFTP: file transfer unsuccessful: " + connectException.getMessage());
        }
    }

    private static void sendDataPacket(DatagramSocket socket, InetAddress dst, byte[] data, byte seq)
            throws IOException {
        // Construct new data array to include sequence number
        byte[] dataWithSeq = new byte[data.length + 1];
        dataWithSeq[0] = seq;
        System.arraycopy(data, 0, dataWithSeq, 1, data.length);

        // Send this data
        DatagramPacket transmitFile = new DatagramPacket(dataWithSeq, dataWithSeq.length, dst, SERVER_PORT);
        DatagramPacket receiveAck = new DatagramPacket(new byte[1], 1);

        for (int retries = RETRANSMISSION_LIMIT; retries >= 0; retries--) {
            try {
                // Send Data Packet
                socket.send(transmitFile);
                if (DEBUG) System.out.println("Packet " + (++packet_count) + " sent , size = " + data.length);

                // Receive Ack
                socket.receive(receiveAck);
                while (receiveAck.getData()[0] != seq) {
                    // Keep Receiving if Ack number is incorrect
                    socket.receive(receiveAck);
                }
                break;
            } catch (SocketTimeoutException socketTimeoutException) {
                // Check Retry Count
                if (retries - 1 < 0) {
                    throw new ConnectException("packet retransmission limit reached");
                }
            }
        }
    }
}
