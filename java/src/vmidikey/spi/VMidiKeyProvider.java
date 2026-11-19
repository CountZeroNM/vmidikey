package vmidikey.spi;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.spi.MidiDeviceProvider;

/**
 * Java の MIDI の拡張の仕組み (MidiDeviceProvider) で、仮想 MIDI 入力の機器 "VMidiKey" を見せる。
 * この jar が classpath にあると、MidiSystem.getMidiDeviceInfo() の一覧に出る
 * (META-INF/services/javax.sound.midi.spi.MidiDeviceProvider で登録)。
 */
public final class VMidiKeyProvider extends MidiDeviceProvider {
    @Override
    public MidiDevice.Info[] getDeviceInfo() {
        return new MidiDevice.Info[] { VMidiKeyDevice.INFO };
    }

    @Override
    public MidiDevice getDevice(MidiDevice.Info info) {
        if (VMidiKeyDevice.INFO.equals(info)) {
            return VMidiKeyDevice.instance();
        }
        throw new IllegalArgumentException("VMidiKey の機器ではありません: " + info);
    }
}
