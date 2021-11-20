import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.Random;

/**
 * Server to receive file over UDP.
 */
public class SftpServer {

    /* Print DEBUG Messages */
    private static final boolean DEBUG = true;

    /* The name of the file to be received from the client (overwrite existing file) */
    private static final String FILENAME = "outputfile";

    /* The port this server listens to */
    private static final int PORT = 9093;

    /* The data size of each packet, except possibly the last one */
    private static final int MAX_DATA_SIZE_PER_PACKET = 512; // bytes

    /* File transfer always starts with this sequence number */
    private static final byte START_SEQ_NUM = (byte) 0;

    /* Simulate real-world Packet Losses and Delay */
    private static final double LOSS_RATE = 0.2;
    private static final int AVERAGE_DELAY = 100;  // milliseconds

    private static int packet_count = 0;

    public static void main(String[] args) throws Exception {

        // Create UDP socket and packet buffer for receiving file
        DatagramSocket socket = new DatagramSocket(PORT);
        DatagramPacket receiveFile = new DatagramPacket(new byte[MAX_DATA_SIZE_PER_PACKET + 1], MAX_DATA_SIZE_PER_PACKET + 1);

        while (true) {

            // Initialize First Sequence Number
            byte expectedSeq = START_SEQ_NUM;

            // Receive first data packet
            byte[] data;
            try {
                data = receiveDataPacket(socket, receiveFile, expectedSeq);
            } catch (IllegalStateException illegalStateException) {
                // Ignore if dropped or out-of-order
                System.out.println("    " + illegalStateException.getMessage());
                continue;
            }

            // Create Output File, and prepare for writing
            File outputFile = new File(FILENAME);
            FileOutputStream outputStream = new FileOutputStream(outputFile, false);

            // Receive rest of data
            boolean ignorePacket = false;
            while (ignorePacket || data.length == MAX_DATA_SIZE_PER_PACKET) {
                if (!ignorePacket) {
                    // write received valid data to file
                    outputStream.write(data);
                    // Flip Expected Sequence Number
                    expectedSeq ^= 1;
                }
                try {
                    // Receive next data packet
                    data = receiveDataPacket(socket, receiveFile, expectedSeq);
                    ignorePacket = false;
                } catch (IllegalStateException illegalStateException) {
                    // Packet dropped or out-of-order -> ignore it for writing
                    System.out.println("     " + illegalStateException.getMessage());
                    ignorePacket = true;
                }
            }

            // Save Last Data Packet & Cleanup
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();
            System.out.println("sFTP: file received successfully from " + receiveFile.getAddress().getHostAddress()
                    + " with total size of " + outputFile.length() + " bytes\n      [" + outputFile.getAbsolutePath() + "]");
        }

    }

    /**
     * Receive a data packet from designated socket and expected sequence number.
     * Packet Loss is simulated by intentionally not replying to received packets with a rate of {@value LOSS_RATE}.
     * A random network delay is added before sending ACK packets, the delay has an average of {@value AVERAGE_DELAY} ms.
     * @param socket UDP socket which the program listens to
     * @param packet Data packet to be received
     * @param seq Expected sequence number
     * @return Extracted data from packet (without sequence number byte)
     * @throws IOException Receiving and sending packets in socket produces this Exception
     * @throws InterruptedException Thread.sleep() produces this Exception
     * @throws IllegalStateException when packet is dropped intentionally and out-of-order packet is received
     */
    private static byte[] receiveDataPacket(DatagramSocket socket, DatagramPacket packet, byte seq)
            throws IOException, InterruptedException, IllegalStateException {
        // Receive a new packet from client
        socket.receive(packet);
        byte[] receivedData = packet.getData();
        if (DEBUG) System.out.println("Packet " + (++packet_count) + " received, size = " + (packet.getLength() - 1));

        Random random = new Random();
        // Simulate Packet Loss
        if (random.nextDouble() < LOSS_RATE) throw new IllegalStateException("Reply ACK not sent.");

        // Simulate network delay
        Thread.sleep((int) (random.nextDouble() * 2 * AVERAGE_DELAY));

        // Send ACK
        DatagramPacket transmitAck = new DatagramPacket(new byte[] {seq}, 1, packet.getSocketAddress());
        socket.send(transmitAck);

        // check sequence number, invalid data if incorrect seq number
        if (seq != receivedData[0]) {
            // Out-of-order Packet Received
            throw new IllegalStateException("Out-of-order Packet Received.");
        }

        // Extract actual file data and return
        byte[] data = new byte[packet.getLength() - 1];
        System.arraycopy(receivedData, 1, data, 0, data.length);
        return data;
    }
}
