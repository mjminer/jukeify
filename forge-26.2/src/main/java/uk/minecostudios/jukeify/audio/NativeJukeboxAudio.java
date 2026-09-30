package uk.minecostudios.jukeify.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NativeJukeboxAudio {
    private static int bufferId;
    private static int sourceId;
    private static boolean loaded;
    private static boolean paused;
    private static BlockPos jukeboxPos;
    private static Path currentFile;
    private static String status = "No local track playing";
    private static float userVolume = 1.0f;

    private NativeJukeboxAudio() {}

    public static boolean isPlaying() {
        if (!loaded || sourceId == 0) return false;
        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        return state == AL10.AL_PLAYING;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean isPaused() {
        return paused;
    }

    public static String getStatus() {
        return status;
    }

    public static String getTrackName() {
        if (currentFile == null) return "";
        String name = currentFile.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static Path getCurrentFile() {
        return currentFile;
    }

    public static int getVolumePercent() {
        return Math.round(userVolume * 100.0f);
    }

    public static void setVolumePercent(int percent) {
        userVolume = Math.max(0.0f, Math.min(1.0f, percent / 100.0f));
        updateGain();
    }

    public static boolean play(Path file, BlockPos pos) {
        stop();
        try {
            DecodedAudio decoded = decode(file);
            if (decoded == null || decoded.pcm() == null || !decoded.pcm().hasRemaining()) {
                status = "Could not decode " + file.getFileName();
                return false;
            }

            bufferId = AL10.alGenBuffers();
            AL10.alBufferData(bufferId, AL10.AL_FORMAT_MONO16, decoded.pcm(), decoded.sampleRate());

            sourceId = AL10.alGenSources();
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcei(sourceId, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
            AL10.alSource3f(sourceId, AL10.AL_POSITION,
                    pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f);
            AL10.alSourcef(sourceId, AL10.AL_REFERENCE_DISTANCE, 4.0f);
            AL10.alSourcef(sourceId, AL10.AL_MAX_DISTANCE, 64.0f);
            AL10.alSourcef(sourceId, AL10.AL_ROLLOFF_FACTOR, 1.0f);
            updateGain();

            AL10.alSourcePlay(sourceId);
            decoded.free();

            loaded = true;
            paused = false;
            jukeboxPos = pos.immutable();
            currentFile = file;
            status = "Playing from jukebox";
            return true;
        } catch (Throwable t) {
            cleanupIds();
            loaded = false;
            paused = false;
            status = "Native audio failed: " + safeMessage(t);
            return false;
        }
    }

    public static void togglePause() {
        if (!loaded || sourceId == 0) return;
        if (paused) {
            AL10.alSourcePlay(sourceId);
            paused = false;
            status = "Playing from jukebox";
        } else {
            AL10.alSourcePause(sourceId);
            paused = true;
            status = "Paused";
        }
    }

    public static void stop() {
        if (sourceId != 0) {
            try { AL10.alSourceStop(sourceId); } catch (Throwable ignored) {}
        }
        cleanupIds();
        loaded = false;
        paused = false;
        currentFile = null;
        jukeboxPos = null;
        status = "No local track playing";
    }

    public static void tick() {
        if (!loaded) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || jukeboxPos == null) {
            stop();
            return;
        }

        if (mc.level.getBlockState(jukeboxPos).getBlock() != Blocks.JUKEBOX) {
            stop();
            return;
        }

        updateGain();

        int state = AL10.alGetSourcei(sourceId, AL10.AL_SOURCE_STATE);
        if (!paused && state == AL10.AL_STOPPED) {
            status = "Track finished";
            cleanupIds();
            loaded = false;
        }
    }

    private static void updateGain() {
        if (sourceId == 0) return;
        Minecraft mc = Minecraft.getInstance();
        float records = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        float master = mc.options.getSoundSourceVolume(SoundSource.MASTER);
        AL10.alSourcef(sourceId, AL10.AL_GAIN, Math.max(0.0f, Math.min(1.0f, userVolume * records * master)));
    }

    private static void cleanupIds() {
        if (sourceId != 0) {
            try { AL10.alDeleteSources(sourceId); } catch (Throwable ignored) {}
            sourceId = 0;
        }
        if (bufferId != 0) {
            try { AL10.alDeleteBuffers(bufferId); } catch (Throwable ignored) {}
            bufferId = 0;
        }
    }

    private static DecodedAudio decode(Path file) throws Exception {
        String lower = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".mp3")) return decodeMp3(file);
        if (lower.endsWith(".ogg")) return decodeOgg(file);
        if (lower.endsWith(".wav")) return decodeWav(file);
        throw new IllegalArgumentException("Supported formats: .mp3, .ogg and .wav");
    }

    private static DecodedAudio decodeMp3(Path file) throws Exception {
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file))) {
            Bitstream bitstream = new Bitstream(input);
            Decoder decoder = new Decoder();
            ByteArrayOutputStream monoBytes = new ByteArrayOutputStream();

            int sampleRate = 44100;
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                try {
                    SampleBuffer samples = (SampleBuffer)decoder.decodeFrame(header, bitstream);
                    sampleRate = samples.getSampleFrequency();
                    int channels = Math.max(1, samples.getChannelCount());
                    short[] data = samples.getBuffer();
                    int length = samples.getBufferLength();

                    for (int i = 0; i < length; i += channels) {
                        int sum = 0;
                        int used = 0;
                        for (int ch = 0; ch < channels && i + ch < length; ch++) {
                            sum += data[i + ch];
                            used++;
                        }
                        short mono = (short)(sum / Math.max(1, used));
                        monoBytes.write(mono & 0xff);
                        monoBytes.write((mono >>> 8) & 0xff);
                    }
                } finally {
                    bitstream.closeFrame();
                }
            }
            bitstream.close();

            byte[] pcmBytes = monoBytes.toByteArray();
            ShortBuffer mono = MemoryUtil.memAllocShort(Math.max(1, pcmBytes.length / 2));
            for (int i = 0; i + 1 < pcmBytes.length; i += 2) {
                int sample = (pcmBytes[i] & 0xff) | (pcmBytes[i + 1] << 8);
                mono.put((short)sample);
            }
            mono.flip();
            return new DecodedAudio(mono, sampleRate, true);
        }
    }

    private static DecodedAudio decodeOgg(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer encoded = MemoryUtil.memAlloc(bytes.length);
        encoded.put(bytes).flip();

        IntBuffer error = MemoryUtil.memAllocInt(1);
        long decoder = STBVorbis.stb_vorbis_open_memory(encoded, error, null);
        if (decoder == MemoryUtil.NULL) {
            int code = error.get(0);
            MemoryUtil.memFree(error);
            MemoryUtil.memFree(encoded);
            throw new IllegalStateException("OGG decoder error " + code);
        }

        STBVorbisInfo info = STBVorbisInfo.malloc();
        STBVorbis.stb_vorbis_get_info(decoder, info);
        int channels = info.channels();
        int sampleRate = info.sample_rate();
        int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

        ShortBuffer interleaved = MemoryUtil.memAllocShort(Math.max(1, totalSamples * channels));
        int frames = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, interleaved);
        interleaved.limit(frames * channels);

        ShortBuffer mono = MemoryUtil.memAllocShort(Math.max(1, frames));
        for (int frame = 0; frame < frames; frame++) {
            int sum = 0;
            for (int ch = 0; ch < channels; ch++) sum += interleaved.get(frame * channels + ch);
            mono.put((short)(sum / Math.max(1, channels)));
        }
        mono.flip();

        MemoryUtil.memFree(interleaved);
        STBVorbis.stb_vorbis_close(decoder);
        info.free();
        MemoryUtil.memFree(error);
        MemoryUtil.memFree(encoded);

        return new DecodedAudio(mono, sampleRate, true);
    }

    private static DecodedAudio decodeWav(Path file) throws Exception {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat src = input.getFormat();
            AudioFormat pcmFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(),
                    16,
                    src.getChannels(),
                    src.getChannels() * 2,
                    src.getSampleRate(),
                    false);

            try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFormat, input);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                pcmStream.transferTo(out);
                byte[] bytes = out.toByteArray();
                int channels = pcmFormat.getChannels();
                int frames = bytes.length / (2 * channels);
                ShortBuffer mono = MemoryUtil.memAllocShort(Math.max(1, frames));

                for (int frame = 0; frame < frames; frame++) {
                    int sum = 0;
                    int base = frame * channels * 2;
                    for (int ch = 0; ch < channels; ch++) {
                        int i = base + ch * 2;
                        int sample = (bytes[i] & 0xff) | (bytes[i + 1] << 8);
                        sum += (short)sample;
                    }
                    mono.put((short)(sum / Math.max(1, channels)));
                }
                mono.flip();
                return new DecodedAudio(mono, Math.round(pcmFormat.getSampleRate()), true);
            }
        }
    }

    private static String safeMessage(Throwable t) {
        String m = t.getMessage();
        if (m == null || m.isBlank()) return t.getClass().getSimpleName();
        return m.length() > 100 ? m.substring(0, 100) : m;
    }

    private record DecodedAudio(ShortBuffer pcm, int sampleRate, boolean nativeBuffer) {
        void free() {
            if (nativeBuffer && pcm != null) MemoryUtil.memFree(pcm);
        }
    }
}
