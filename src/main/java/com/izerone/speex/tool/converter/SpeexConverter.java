package com.izerone.speex.tool.converter;

import org.xiph.speex.*;

import java.io.*;

/**
 * Speex 音频转换器
 *
 * @author izerone
 * @date 2022-02-15
 */
public class SpeexConverter {

    /**
     * 转换 spx 文件到 wav
     *
     * @param spxFile spx 文件
     * @param wavFile wav 文件
     */
    public void convert2wav(File spxFile, File wavFile) throws IOException {
        byte[] header = new byte[2048];
        byte[] payload = new byte[65536];
        byte[] decdat = new byte[44100 * 2 * 2];
        final int WAV_HEADER_SIZE = 8;
        final short WAVE_FORMAT_SPEEX = (short) 0xa109;
        final String RIFF = "RIFF";
        final String WAVE = "WAVE";
        final String FORMAT = "fmt ";
        final String DATA = "data";
        final int OGG_HEADER_SIZE = 27;
        final int OGG_SEGOFFSET = 26;
        final String OGGID = "OggS";
        int segments = 0;
        int curseg = 0;
        int bodybytes = 0;
        int decsize = 0;
        int packetNo = 0;
        // speex 解码器
        SpeexDecoder speexDecoder = new SpeexDecoder();
        // 打开文件输入流
        DataInputStream dis = new DataInputStream(new FileInputStream(spxFile));

        AudioFileWriter writer = null;
        int origchksum;
        int chksum;
        try {
            // read until we get to EOF
            while (true) {
                if (srcFormat == FILE_FORMAT_OGG) {
                    // read the OGG header
                    dis.readFully(header, 0, OGG_HEADER_SIZE);
                    origchksum = readInt(header, 22);
                    header[22] = 0;
                    header[23] = 0;
                    header[24] = 0;
                    header[25] = 0;
                    chksum = OggCrc.checksum(0, header, 0, OGG_HEADER_SIZE);

                    // make sure its a OGG header
                    if (!OGGID.equals(new String(header, 0, 4))) {
                        System.err.println("missing ogg id!");
                        return;
                    }

                    /* how many segments are there? */
                    segments = header[OGG_SEGOFFSET] & 0xFF;
                    dis.readFully(header, OGG_HEADER_SIZE, segments);
                    chksum = OggCrc.checksum(chksum, header, OGG_HEADER_SIZE, segments);

                    /* decode each segment, writing output to wav */
                    for (curseg = 0; curseg < segments; curseg++) {
                        /* get the number of bytes in the segment */
                        bodybytes = header[OGG_HEADER_SIZE + curseg] & 0xFF;
                        if (bodybytes == 255) {
                            System.err.println("sorry, don't handle 255 sizes!");
                            return;
                        }
                        dis.readFully(payload, 0, bodybytes);
                        chksum = OggCrc.checksum(chksum, payload, 0, bodybytes);

                        /* decode the segment */
                        /* if first packet, read the Speex header */
                        if (packetNo == 0) {
                            if (readSpeexHeader(payload, 0, bodybytes)) {
                                if (printlevel <= DEBUG) {
                                    System.out.println("File Format: Ogg Speex");
                                    System.out.println("Sample Rate: " + sampleRate);
                                    System.out.println("Channels: " + channels);
                                    System.out.println("Encoder mode: " + (mode == 0 ? "Narrowband" : (mode == 1 ? "Wideband" : "UltraWideband")));
                                    System.out.println("Frames per packet: " + nframes);
                                }
                                /* once Speex header read, initialize the wave writer with output format */
                                if (destFormat == FILE_FORMAT_WAVE) {
                                    writer = new PcmWaveWriter(speexDecoder.getSampleRate(),
                                            speexDecoder.getChannels());
                                    if (printlevel <= DEBUG) {
                                        System.out.println("");
                                        System.out.println("Output File: " + destPath);
                                        System.out.println("File Format: PCM Wave");
                                        System.out.println("Perceptual Enhancement: " + enhanced);
                                    }
                                } else {
                                    writer = new RawWriter();
                                    if (printlevel <= DEBUG) {
                                        System.out.println("");
                                        System.out.println("Output File: " + destPath);
                                        System.out.println("File Format: Raw Audio");
                                        System.out.println("Perceptual Enhancement: " + enhanced);
                                    }
                                }
                                writer.open(destPath);
                                writer.writeHeader(null);
                                packetNo++;
                            } else {
                                packetNo = 0;
                            }
                        } else if (packetNo == 1) { // Ogg Comment packet
                            packetNo++;
                        } else {
                            if (loss > 0 && random.nextInt(100) < loss) {
                                speexDecoder.processData(null, 0, bodybytes);
                                for (int i = 1; i < nframes; i++) {
                                    speexDecoder.processData(true);
                                }
                            } else {
                                speexDecoder.processData(payload, 0, bodybytes);
                                for (int i = 1; i < nframes; i++) {
                                    speexDecoder.processData(false);
                                }
                            }
                            /* get the amount of decoded data */
                            if ((decsize = speexDecoder.getProcessedData(decdat, 0)) > 0) {
                                writer.writePacket(decdat, 0, decsize);
                            }
                            packetNo++;
                        }
                    }
                    if (chksum != origchksum) {
                        throw new IOException("Ogg CheckSums do not match");
                    }
                } else { // Wave or Raw Speex
                    /* if first packet, initialise everything */
                    if (packetNo == 0) {
                        if (srcFormat == FILE_FORMAT_WAVE) {
                            // read the WAVE header
                            dis.readFully(header, 0, WAV_HEADER_SIZE + 4);
                            // make sure its a WAVE header
                            if (!RIFF.equals(new String(header, 0, 4)) &&
                                    !WAVE.equals(new String(header, 8, 4))) {
                                System.err.println("Not a WAVE file");
                                return;
                            }
                            // Read other header chunks
                            dis.readFully(header, 0, WAV_HEADER_SIZE);
                            String chunk = new String(header, 0, 4);
                            int size = readInt(header, 4);
                            while (!chunk.equals(DATA)) {
                                dis.readFully(header, 0, size);
                                if (chunk.equals(FORMAT)) {
                                    if (readShort(header, 0) != WAVE_FORMAT_SPEEX) {
                                        System.err.println("Not a Wave Speex file");
                                        return;
                                    }
                                    channels = readShort(header, 2);
                                    sampleRate = readInt(header, 4);
                                    bodybytes = readShort(header, 12);
                                    if (readShort(header, 16) < 82) {
                                        System.err.println("Possibly corrupt Speex Wave file.");
                                        return;
                                    }
                                    readSpeexHeader(header, 20, 80);
                                    // Display audio info
                                    if (printlevel <= DEBUG) {
                                        System.out.println("File Format: Wave Speex");
                                        System.out.println("Sample Rate: " + sampleRate);
                                        System.out.println("Channels: " + channels);
                                        System.out.println("Encoder mode: " + (mode == 0 ? "Narrowband" : (mode == 1 ? "Wideband" : "UltraWideband")));
                                        System.out.println("Frames per packet: " + nframes);
                                    }
                                }
                                dis.readFully(header, 0, WAV_HEADER_SIZE);
                                chunk = new String(header, 0, 4);
                                size = readInt(header, 4);
                            }
                            if (printlevel <= DEBUG) System.out.println("Data size: " + size);
                        } else {
                            if (printlevel <= DEBUG) {
                                System.out.println("File Format: Raw Speex");
                                System.out.println("Sample Rate: " + sampleRate);
                                System.out.println("Channels: " + channels);
                                System.out.println("Encoder mode: " + (mode == 0 ? "Narrowband" : (mode == 1 ? "Wideband" : "UltraWideband")));
                                System.out.println("Frames per packet: " + nframes);
                            }
                            /* initialize the Speex decoder */
                            speexDecoder.init(mode, sampleRate, channels, enhanced);
                            if (!vbr) {
                                switch (mode) {
                                    case 0:
                                        bodybytes = NbEncoder.NB_FRAME_SIZE[NbEncoder.NB_QUALITY_MAP[quality]];
                                        break;
//Wideband
                                    case 1:
                                        bodybytes = SbEncoder.NB_FRAME_SIZE[SbEncoder.NB_QUALITY_MAP[quality]];
                                        bodybytes += SbEncoder.SB_FRAME_SIZE[SbEncoder.WB_QUALITY_MAP[quality]];
                                        break;
                                    case 2:
                                        bodybytes = SbEncoder.NB_FRAME_SIZE[SbEncoder.NB_QUALITY_MAP[quality]];
                                        bodybytes += SbEncoder.SB_FRAME_SIZE[SbEncoder.WB_QUALITY_MAP[quality]];
                                        bodybytes += SbEncoder.SB_FRAME_SIZE[SbEncoder.UWB_QUALITY_MAP[quality]];
                                        break;
//*/
                                    default:
                                        throw new IOException("Illegal mode encoundered.");
                                }
                                bodybytes = (bodybytes + 7) >> 3;
                            } else {
                                // We have read the stream to find out more
                                bodybytes = 0;
                            }
                        }
                        /* initialize the wave writer with output format */
                        if (destFormat == FILE_FORMAT_WAVE) {
                            writer = new PcmWaveWriter(sampleRate, channels);
                            if (printlevel <= DEBUG) {
                                System.out.println("");
                                System.out.println("Output File: " + destPath);
                                System.out.println("File Format: PCM Wave");
                                System.out.println("Perceptual Enhancement: " + enhanced);
                            }
                        } else {
                            writer = new RawWriter();
                            if (printlevel <= DEBUG) {
                                System.out.println("");
                                System.out.println("Output File: " + destPath);
                                System.out.println("File Format: Raw Audio");
                                System.out.println("Perceptual Enhancement: " + enhanced);
                            }
                        }
                        writer.open(destPath);
                        writer.writeHeader(null);
                        packetNo++;
                    } else {
                        dis.readFully(payload, 0, bodybytes);
                        if (loss > 0 && random.nextInt(100) < loss) {
                            speexDecoder.processData(null, 0, bodybytes);
                            for (int i = 1; i < nframes; i++) {
                                speexDecoder.processData(true);
                            }
                        } else {
                            speexDecoder.processData(payload, 0, bodybytes);
                            for (int i = 1; i < nframes; i++) {
                                speexDecoder.processData(false);
                            }
                        }
                        /* get the amount of decoded data */
                        if ((decsize = speexDecoder.getProcessedData(decdat, 0)) > 0) {
                            writer.writePacket(decdat, 0, decsize);
                        }
                        packetNo++;
                    }
                }
            }
        } catch (IOException eof) {
        }
        /* close the output file */
        writer.close();
        // TODO
    }

    /**
     * 转换 spx 文件到 mp3
     *
     * @param spxFile spx 文件
     * @param mp3File mp3 文件
     */
    public void convert2mp3(File spxFile, File mp3File) {
        // TODO
    }

    /**
     * Reads the header packet.
     * <pre>
     *  0 -  7: speex_string: "Speex   "
     *  8 - 27: speex_version: "speex-1.0"
     * 28 - 31: speex_version_id: 1
     * 32 - 35: header_size: 80
     * 36 - 39: rate
     * 40 - 43: mode: 0=narrowband, 1=wb, 2=uwb
     * 44 - 47: mode_bitstream_version: 4
     * 48 - 51: nb_channels
     * 52 - 55: bitrate: -1
     * 56 - 59: frame_size: 160
     * 60 - 63: vbr
     * 64 - 67: frames_per_packet
     * 68 - 71: extra_headers: 0
     * 72 - 75: reserved1
     * 76 - 79: reserved2
     * </pre>
     *
     * @param packet
     * @param offset
     * @param bytes
     * @return
     */
    private boolean readSpeexHeader(final byte[] packet,
                                    final int offset,
                                    final int bytes) {
        if (bytes != 80) {
            System.out.println("Oooops");
            return false;
        }
        if (!"Speex   ".equals(new String(packet, offset, 8))) {
            return false;
        }
        mode = packet[40 + offset] & 0xFF;
        sampleRate = readInt(packet, offset + 36);
        channels = readInt(packet, offset + 48);
        nframes = readInt(packet, offset + 64);
        return speexDecoder.init(mode, sampleRate, channels, enhanced);
    }

    /**
     * Converts Little Endian (Windows) bytes to an int (Java uses Big Endian).
     *
     * @param data   the data to read.
     * @param offset the offset from which to start reading.
     * @return the integer value of the reassembled bytes.
     */
    protected static int readInt(final byte[] data, final int offset) {
        return (data[offset] & 0xff) |
                ((data[offset + 1] & 0xff) << 8) |
                ((data[offset + 2] & 0xff) << 16) |
                (data[offset + 3] << 24); // no 0xff on the last one to keep the sign
    }

    /**
     * Converts Little Endian (Windows) bytes to an short (Java uses Big Endian).
     *
     * @param data   the data to read.
     * @param offset the offset from which to start reading.
     * @return the integer value of the reassembled bytes.
     */
    protected static int readShort(final byte[] data, final int offset) {
        return (data[offset] & 0xff) |
                (data[offset + 1] << 8); // no 0xff on the last one to keep the sign
    }
}
