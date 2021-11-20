import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Client to send out ping requests over UDP.
 */
public class PingClient {

    /* Number of ping requests to be sent to the server */
    private static final int PING_REQUESTS = 5;

    /* Interval between each ping request */
    private static final int SLEEP_INTERVAL = 1000; // milliseconds

    /* Timeout when waiting for server reply */
    private static final int SOCKET_TIMEOUT = 1000; // milliseconds

    /* Random generated packet data length */
    private static final int MESSAGE_LENGTH = 56; // bytes

    public static void main(String[] args) throws Exception {

        // Get Command Line Arguments
        if (args.length != 2) {
            System.out.println("Correct Usage: \njava PingClient <host-name> <port-number>");
            return;
        }
        String hostname = args[0];
        int port = Integer.parseInt(args[1]);
        InetAddress serverAddress = InetAddress.getByName(hostname);
        assert serverAddress.isReachable(1000) : "The hostname <" + hostname + "> is not reachable!";

        // Create socket for sending ping requests
        DatagramSocket socket = new DatagramSocket(); // Client uses random port
        socket.setSoTimeout(SOCKET_TIMEOUT);

        // Send Ping Requests
        for (int i = 0; i < PING_REQUESTS; i++) {
            // Create packet with random contents
            byte[] data = generateRandMsg();
            DatagramPacket pingRequest = new DatagramPacket(data, MESSAGE_LENGTH, serverAddress, port);
            DatagramPacket pingResponse = new DatagramPacket(new byte[MESSAGE_LENGTH], MESSAGE_LENGTH);

            try {
                // Send Ping packet
                socket.send(pingRequest);

                // Receive Reply
                long startTime = System.nanoTime(); // Starting counting time after packet sent
                socket.receive(pingResponse);
                long endTime = System.nanoTime(); // Stop timer

                // Check received data integrity
                assert pingResponse.getData() == data : "ERROR: Received Incorrect Message.";

                // Print Result
                System.out.println("PING " + pingResponse.getAddress().getHostAddress() + " " + i + " "
                        + String.format("%.3f", TimeUnit.MICROSECONDS.convert((endTime - startTime), TimeUnit.NANOSECONDS) / 1000.0));

            } catch (SocketTimeoutException socketTimeoutException) {
                // Timeout occurred
                System.out.println("PING " + pingRequest.getAddress().getHostAddress() + " " + i + " LOST");
            }

            Thread.sleep(SLEEP_INTERVAL);
        }

    }

    /**
     * Generate a random packet data using common characters.
     * @return byte[] generated random data
     */
    private static byte[] generateRandMsg() {
        String str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        byte[] msg = new byte[MESSAGE_LENGTH];
        for (int i = 0; i < MESSAGE_LENGTH; i++) {
            msg[i] = (byte) str.charAt(random.nextInt(str.length()));
        }
        return msg;
    }
}
