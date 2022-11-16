import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertTrue;

public class IO {
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().dynamicPort());

    private String REQUESTED_RESOURCE = "/test.json";


    @Before
    public void setup() {
        stubFor(get(urlEqualTo(REQUESTED_RESOURCE))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("{ \"response\" : \"It worked!\" }")));
    }

    @Test
    public void givenJavaIOSocket_whenReadingAndWritingWithStreams_thenSuccess() throws IOException {
        // given an IO socket and somewhere to store our result
        Socket socket = new Socket("localhost", wireMockRule.port());
        StringBuilder ourStore = new StringBuilder();

        // when we write and read (using try-with-resources so our resources are auto-closed)
        try (InputStream serverInput = socket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(serverInput));
             OutputStream clientOutput = socket.getOutputStream();
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(clientOutput))) {
            writer.print("GET " + REQUESTED_RESOURCE + " HTTP/1.0\r\n\r\n");
            writer.flush(); // important - without this the request is never sent, and the test will hang on readLine()

            for (String line; (line = reader.readLine()) != null; ) {
                ourStore.append(line);
                ourStore.append(System.lineSeparator());
            }
        }

        // then we read and saved our data
        assertTrue(ourStore
                .toString()
                .contains("It worked!"));
        System.out.println(ourStore.toString());
    }


    @Test
    public void givenJavaNIOSocketChannel_whenReadingAndWritingWithBuffers_thenSuccess() throws IOException {
        // given a NIO SocketChannel and a charset
        InetSocketAddress address = new InetSocketAddress("localhost", wireMockRule.port());
        SocketChannel socketChannel = SocketChannel.open(address);
        Charset charset = StandardCharsets.UTF_8;

        // when we write and read using buffers
        socketChannel.write(charset.encode(CharBuffer.wrap("GET " + REQUESTED_RESOURCE + " HTTP/1.0\r\n\r\n")));

        ByteBuffer byteBuffer = ByteBuffer.allocate(8192); // or allocateDirect if we need direct memory access
        CharBuffer charBuffer = CharBuffer.allocate(8192);
        CharsetDecoder charsetDecoder = charset.newDecoder();
        StringBuilder ourStore = new StringBuilder();
        while (socketChannel.read(byteBuffer) != -1 || byteBuffer.position() > 0) {
            byteBuffer.flip();
            storeBufferContents(byteBuffer, charBuffer, charsetDecoder, ourStore);
            byteBuffer.compact();
        }
        socketChannel.close();

        // then we read and saved our data
        assertTrue(ourStore
                .toString()
                .contains("It worked!"));
    }

    @Test
    public void givenJavaNIOSocketChannel_whenReadingAndWritingWithSmallBuffers_thenSuccess() throws IOException {
        // given a NIO SocketChannel and a charset
        InetSocketAddress address = new InetSocketAddress("localhost", wireMockRule.port());
        SocketChannel socketChannel = SocketChannel.open(address);
        Charset charset = StandardCharsets.UTF_8;

        // when we write and read using buffers that are too small for our message
        socketChannel.write(charset.encode(CharBuffer.wrap("GET " + REQUESTED_RESOURCE + " HTTP/1.0\r\n\r\n")));

        ByteBuffer byteBuffer = ByteBuffer.allocate(8); // or allocateDirect if we need direct memory access
        CharBuffer charBuffer = CharBuffer.allocate(8);
        CharsetDecoder charsetDecoder = charset.newDecoder();
        StringBuilder ourStore = new StringBuilder();
        while (socketChannel.read(byteBuffer) != -1 || byteBuffer.position() > 0) {
            byteBuffer.flip();
            storeBufferContents(byteBuffer, charBuffer, charsetDecoder, ourStore);
            byteBuffer.compact();
        }
        socketChannel.close();

        // then we read and saved our data
        assertTrue(ourStore
                .toString()
                .contains("It worked!"));
    }

    void storeBufferContents(ByteBuffer byteBuffer, CharBuffer charBuffer, CharsetDecoder charsetDecoder, StringBuilder ourStore) {
        charsetDecoder.decode(byteBuffer, charBuffer, true);
        charBuffer.flip();
        ourStore.append(charBuffer);
        charBuffer.clear();
    }

}
