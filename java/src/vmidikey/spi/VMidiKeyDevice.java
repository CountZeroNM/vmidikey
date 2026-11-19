package vmidikey.spi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Transmitter;

/**
 * 仮想 MIDI 入力の機器。開くと 127.0.0.1 の UDP ポート (既定 47100) で待ち受け、
 * 届いたデータグラム 1 つを MIDI メッセージ 1 つとして、送信口 (Transmitter) につながった受け手へ渡す。
 * ポートはシステムプロパティ vmidikey.port か環境変数 VMIDIKEY_PORT で変えられる。
 * 受け手が無い間に届いたものは捨てる。入力専用 (受信口 Receiver は無い)。
 */
public final class VMidiKeyDevice implements MidiDevice {
    public static final int DEFAULT_PORT = 47100;

    static final MidiDevice.Info INFO = new Info();

    private static final class Info extends MidiDevice.Info {
        Info() {
            super("VMidiKey", "vmidikey", "仮想 MIDI キーボード (UDP 127.0.0.1)", "0.1");
        }
    }

    private static final VMidiKeyDevice INSTANCE = new VMidiKeyDevice();

    static VMidiKeyDevice instance() {
        return INSTANCE;
    }

    private final List<Transmitter> transmitters = new CopyOnWriteArrayList<>();
    private volatile DatagramSocket socket;
    private volatile Thread reader;

    private VMidiKeyDevice() {}

    /** 待ち受けるポート (システムプロパティ → 環境変数 → 既定)。 */
    public static int port() {
        String p = System.getProperty("vmidikey.port");
        if (p == null) {
            p = System.getenv("VMIDIKEY_PORT");
        }
        try {
            return p == null ? DEFAULT_PORT : Integer.parseInt(p.trim());
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    @Override
    public MidiDevice.Info getDeviceInfo() {
        return INFO;
    }

    @Override
    public synchronized void open() throws MidiUnavailableException {
        if (isOpen()) {
            return;
        }
        try {
            DatagramSocket s = new DatagramSocket(port(), InetAddress.getLoopbackAddress());
            socket = s;
            Thread t = new Thread(() -> readLoop(s), "vmidikey-udp");
            t.setDaemon(true);
            reader = t;
            t.start();
        } catch (SocketException e) {
            MidiUnavailableException m =
                new MidiUnavailableException("VMidiKey: UDP ポート " + port() + " を開けません: " + e.getMessage());
            m.initCause(e);
            throw m;
        }
    }

    private void readLoop(DatagramSocket s) {
        byte[] buf = new byte[4096];
        while (!s.isClosed()) {
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            try {
                s.receive(p);
            } catch (IOException e) {
                return; // 閉じられた
            }
            MidiMessage m = toMessage(Arrays.copyOf(p.getData(), p.getLength()));
            if (m != null) {
                for (Transmitter t : transmitters) {
                    Receiver r = t.getReceiver();
                    if (r != null) {
                        r.send(m, -1);
                    }
                }
            }
        }
    }

    /** バイト列 → MIDI メッセージ (チャンネルメッセージ・F0 で始まるエクスクルーシブ)。解釈できなければ null。 */
    public static MidiMessage toMessage(byte[] b) {
        if (b.length == 0) {
            return null;
        }
        int status = b[0] & 0xFF;
        try {
            if (status == 0xF0) {
                return new SysexMessage(b, b.length);
            }
            if (status >= 0x80 && status < 0xF0) {
                int d1 = b.length > 1 ? b[1] & 0x7F : 0;
                int d2 = b.length > 2 ? b[2] & 0x7F : 0;
                return new ShortMessage(status, d1, d2);
            }
            if (status >= 0xF8) { // リアルタイム (クロック・スタート・ストップなど)
                return new ShortMessage(status);
            }
        } catch (InvalidMidiDataException e) {
            return null;
        }
        return null;
    }

    @Override
    public synchronized void close() {
        DatagramSocket s = socket;
        socket = null;
        if (s != null) {
            s.close();
        }
        reader = null;
        for (Transmitter t : transmitters) {
            t.close();
        }
        transmitters.clear();
    }

    @Override
    public boolean isOpen() {
        DatagramSocket s = socket;
        return s != null && !s.isClosed();
    }

    @Override
    public long getMicrosecondPosition() {
        return -1;
    }

    @Override
    public int getMaxReceivers() {
        return 0;
    }

    @Override
    public int getMaxTransmitters() {
        return -1;
    }

    @Override
    public Receiver getReceiver() throws MidiUnavailableException {
        throw new MidiUnavailableException("VMidiKey は入力専用です");
    }

    @Override
    public List<Receiver> getReceivers() {
        return new ArrayList<>();
    }

    @Override
    public Transmitter getTransmitter() {
        Transmitter t = new Transmitter() {
            private volatile Receiver receiver;

            @Override
            public void setReceiver(Receiver r) {
                receiver = r;
            }

            @Override
            public Receiver getReceiver() {
                return receiver;
            }

            @Override
            public void close() {
                transmitters.remove(this);
            }
        };
        transmitters.add(t);
        return t;
    }

    @Override
    public List<Transmitter> getTransmitters() {
        return new ArrayList<>(transmitters);
    }
}
