import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class SftpServer {

    /* Print DEBUG Messages */
    private static final boolean DEBUG = false;

    /* The name of the file to be received from the client (overwrite existing file) */
    private static final String FILENAME = "outputfile";

    /* The port this server listens to */
    private static final int PORT = 9093;

    /* The data size of each packet, except possibly the last one */
    private static final int MAX_DATA_SIZE_PER_PACKET = 512; // bytes

    /* File transfer always starts with this sequence number */
    private static final byte START_SEQ_NUM = (byte) 0;

    private static final double LOSS_RATE = 0.2;
    private static final int AVERAGE_DELAY = 100;  // milliseconds

    private static int packet_count = 0;

    public static void main(String[] args) throws Exception {

        // Create UDP socket and packet buffer for receiving file
        DatagramSocket socket = new DatagramSocket(PORT);
        DatagramPacket receiveFile = new DatagramPacket(new byte[MAX_DATA_SIZE_PER_PACKET + 1], MAX_DATA_SIZE_PER_PACKET + 1);

        while (true) {

            // Receive first data packet
            byte expectedSeq = START_SEQ_NUM;
            byte[] data = receiveDataPacket(socket, receiveFile, expectedSeq);

            // Create Output File, and prepare for writing
            File outputFile = new File(FILENAME);
            FileOutputStream outputStream = new FileOutputStream(outputFile, false);

            while (data.length == MAX_DATA_SIZE_PER_PACKET) {
                outputStream.write(data); // write received data to file
                // Flip Expected Sequence Number
                expectedSeq ^= 1;
                // Receive next data packet
                data = receiveDataPacket(socket, receiveFile, expectedSeq);
            }

            // Save Last Data Packet
            outputStream.write(data);
            outputStream.flush();
            outputStream.close();
            System.out.println("sFTP: file received successfully from " + receiveFile.getAddress().getHostAddress()
                    + " with total size of " + outputFile.length() + " bytes");
        }

    }

    private static byte[] receiveDataPacket(DatagramSocket socket, DatagramPacket packet, byte seq) throws IOException {
        // Receive a new packet from client
        socket.receive(packet);
        byte[] receivedData = packet.getData();
        if (DEBUG) System.out.println("Packet " + (++packet_count) + " received, size = " + (packet.getLength() - 1));

        // check sequence number
        if (seq != receivedData[0]) {
            // Out-of-order Packet Received
            throw new IllegalStateException("ERROR: Out-of-order Packet");
        }
        // TODO: Randomly drop packets, add delay
        DatagramPacket transmitAck = new DatagramPacket(new byte[] {seq}, 1, packet.getSocketAddress());
        socket.send(transmitAck);

        byte[] data = new byte[packet.getLength() - 1];
        System.arraycopy(receivedData, 1, data, 0, data.length);
        return data;
    }
}
