package nl.kuijp.horizoncontrol;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

class RfbProto {

    private final static int secTypeNone = 1;
    private final static String versionMsg_3_8 = "RFB 003.008\n";
    private final static int VncAuthOK = 0, VncAuthFailed = 1,
            VncAuthTooMany = 2;
    private final static int keyboardEvent = 4;

    private OutputStream os;
    private byte[] eventBuf = new byte[72];
    private int eventBufLen = 0;
    private Socket sock;
    private DataInputStream is;

    public RfbProto(String host, int port) throws Exception {
        initConnection(host, port);
    }

    public void writeKeyDown(int key) {
        writeKeyEvent(key, true);
    }

    public void writeKeyUp(int key) {
        writeKeyEvent(key, false);
    }

    public void writeBuffer() throws IOException {
        os.write(eventBuf, 0, eventBufLen);
        eventBufLen = 0;
        os.flush();
    }

    public synchronized void close() {
        try {
            sock.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void initConnection(String host, int port) throws Exception {
        sock = new Socket(host, port);
        is = new DataInputStream(new BufferedInputStream(sock.getInputStream(),
                16384));
        os = sock.getOutputStream();
        readVersionMsg();
        writeVersionMsg();
        selectSecurityTypeNone();
        authenticate();
        os.write(0);
    }

    private void readVersionMsg() throws Exception {
        byte[] b = new byte[12];
        readFully(b);
    }

    private void writeVersionMsg() throws IOException {
        os.write(versionMsg_3_8.getBytes());
    }

    private void writeKeyEvent(int key, boolean down) {
        eventBuf[eventBufLen++] = (byte) keyboardEvent;
        eventBuf[eventBufLen++] = (byte) (down ? 1 : 0);
        eventBuf[eventBufLen++] = (byte) 0;
        eventBuf[eventBufLen++] = (byte) 0;
        eventBuf[eventBufLen++] = (byte) ((key >> 24) & 0xff);
        eventBuf[eventBufLen++] = (byte) ((key >> 16) & 0xff);
        eventBuf[eventBufLen++] = (byte) ((key >> 8) & 0xff);
        eventBuf[eventBufLen++] = (byte) (key & 0xff);
    }

    private void selectSecurityTypeNone() throws Exception {
        int nSecTypes = readU8();
        byte[] secTypes = new byte[nSecTypes];
        readFully(secTypes);
        os.write(secTypeNone);
    }

    private void readConnFailedReason() throws Exception {
        int reasonLen = readU32();
        byte[] reason = new byte[reasonLen];
        readFully(reason);
        throw new Exception(new String(reason));
    }

    private void authenticate() throws Exception {
        int securityResult = readU32();
        switch (securityResult) {
            case VncAuthOK:
                break;
            case VncAuthFailed:
                readConnFailedReason();
                throw new Exception("No Authentication" + ": failed");
            case VncAuthTooMany:
                throw new Exception("No authentication"
                        + ": failed, too many tries");
            default:
                throw new Exception("No authentication" + ": unknown result "
                        + securityResult);
        }
    }

    private void readFully(byte b[]) throws IOException {
        readFully(b, 0, b.length);
    }

    private void readFully(byte b[], int off, int len) throws IOException {
        is.readFully(b, off, len);
    }

    private int readU8() throws IOException {
        return is.readUnsignedByte();
    }

    private int readU32() throws IOException {
        return is.readInt();
    }
}
