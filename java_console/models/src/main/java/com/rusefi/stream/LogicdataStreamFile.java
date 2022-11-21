package com.rusefi.stream;

import com.rusefi.composite.CompositeEvent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Support for Saleae .logicdata format.
 * (c) andreika 2020
 * <p>
 * Jun 7 status: this code mostly works but it needs more testing
 *
 * @see LogicdataStreamFileSandbox
 */
public class LogicdataStreamFile extends StreamFile {

    private static final int frequency = 1000000;
    private static final int frequencyDiv = 10;

	private static final char magic = 0x7f;
	private static final String title = "Data save2";

	private static final int BLOCK = 0x15;
	private static final int CHANNEL_BLOCK = 0x16;
	private static final int SUB = 0x54;

	private static final int FLAG_NOTEMPTY = 2;
	private static final int FLAG_NOTEMPTY_LONG = 3;
	private static final int FLAG_EMPTY = 5;

	private static final int LOGIC4 = 0x40FD;
	private static final int LOGIC8 = 0x673B;

	private static final int [] CHANNEL_FLAGS = { 0x13458b, 0x0000ff, 0x00a0f9, 0x00ffff, 0x00ff00, 0xff0000, 0xf020a0, };

	private static final long SIGN_FLAG = 0x80000000L;

    private static final int numChannels = 6;
    private static final int reservedDurationInSamples = 10;
    private static int realDurationInSamples = 0;
    private static int scaledDurationInSamples = 0;

	private final String fileName;
	private final List<CompositeEvent> eventsBuffer = new ArrayList<>();

	private static final String [] channelNames = { "Primary", "Secondary", "Trg", "Sync", "Coil", "Injector", "Channel 6", "Channel 7" };


	public LogicdataStreamFile(String fileName) {
		this.fileName = fileName;
	}

	/*
        public static void writeLogicdata(List<CompositeEvent> events, OutputStream stream) throws IOException {
            LogicdataStreamFile saver = new LogicdataStreamFile();
            saver.stream = stream;
            saver.writeHeader();
            saver.appendEvents(events);
            saver.close();
        }
    */
    @Override
    public void append(List<CompositeEvent> events) {
        try {
            if (stream == null) {
                stream = new LogicdataOutputStream(Files.newOutputStream(Paths.get(fileName)));
                writeHeader();
            }
            eventsBuffer.addAll(events);
        } catch (IOException e) {
            // ignoring this one
        }
    }

	/**
	 * this file format is not streaming, we have to write everything at once
	 */
	private void writeEvents(List<CompositeEvent> events) throws IOException {
    	// we need at least 2 records
    	if (events == null || events.size() < 2)
    		return;
		long firstRecordTs = events.get(1).getTimestamp(); // huh why not index '0'?
		long lastRecordTs = events.get(events.size() - 1).getTimestamp();
	    // we don't know the total duration, so we create a margin after the last record which equals to the duration of the first event
		// TODO: why do we jump from timestamps to samples?
	    realDurationInSamples = (int)(lastRecordTs + firstRecordTs);
    	scaledDurationInSamples = realDurationInSamples / 4;

		writeChannelDataHeader();

		boolean useLongDeltas = false;
    	// we need to split the combined events into separate channels
    	for (int ch = 0; ch < numChannels; ch++) {
			List<Long> chDeltas = new ArrayList<>();
			int chPrevState = -1;
			long prevTs = 0;
        	for (CompositeEvent event : events) {
        		int chState = getChannelState(ch, event);
        		long ts = event.getTimestamp();

        		if (chPrevState == -1) {
        			chPrevState = chState;
        		}
				if (chState != chPrevState) {
					long delta = ts - prevTs;
					if (delta > 0x7fff) {
						useLongDeltas = true;
					}
					// encode state
					if (chState == 0)
						delta |= SIGN_FLAG;

					chDeltas.add(delta);

					prevTs = ts;
					chPrevState = chState;
				}
			}

        	// TODO: why do we pass a timestamp as a record index?
			writeChannelData(ch, chDeltas, chPrevState, (int)prevTs, useLongDeltas);
        }

        writeChannelDataFooter();
        stream.flush();
    }

    private int getChannelState(int ch, CompositeEvent event) {
		switch (ch) {
		case 0:
			return event.isPrimaryTriggerAsInt();
		case 1:
            return event.isSecondaryTriggerAsInt();
		case 2:
            return event.isTrgAsInt();
		case 3:
			return event.isSyncAsInt();
		case 4:
			return event.isCoil();
		case 5:
            return event.isInjector();
		}
		return -1;
	}

    ////////////////////////////////////////////////////////////////////

    private void writeHeader() throws IOException {
        writeByte(magic);

        writeVarLength(title.length());
        write(title);
        stream.flush();

        writeVarLength(BLOCK);
        writeVarLength(SUB);
        writeVarLength(frequency);
        writeVarLength(0);
        writeVarLength(reservedDurationInSamples);
        writeVarLength(frequency / frequencyDiv);
        write(0, 2);
        writeVarLength(numChannels);

        writeVarLength(BLOCK);
        writeVarLength(0);

        writeVarLength(BLOCK);
        for (int i = 0; i < numChannels; i++) {
        	writeId(i, 1);
		}
        writeVarLength(0);

        writeVarLength(BLOCK);

		writeId(0, 0);
		writeVarLength(0);
		writeVarLength(0);
    }

    private void writeChannelHeader(int ch) throws IOException {
		writeVarLength(0xff);
		writeVarLength(ch);
		write(channelNames[ch]);
		write(0, 2);
		writeDouble(1.0);
		writeVarLength(0);
		writeDouble(0.0);
		writeVarLength(1);	// or 2
		writeDouble(0.0);	// or 1.0

		// this part sounds like the 'next' pointer?
		if (ch == numChannels - 1) {
			writeVarLength(0);
		} else {
			writeId(1 + ch, 1);
			for (int i = 0; i < 3; i++) {
				writeVarLength((CHANNEL_FLAGS[ch] >> (i * 8)) & 0xff);
			}
		}
    }

    private void writeChannelDataHeader() throws IOException {
		writeVarLength(BLOCK);
		writeVarLength(scaledDurationInSamples);
		write(0, 5);
		writeVarLength(numChannels);
		write(0, 3);
		writeId(0, 1);
		writeVarLength(0);

        writeVarLength(BLOCK);
		write(0, 3);

		for (int i = 0; i < numChannels; i++) {
			writeChannelHeader(i);
		}

        writeVarLength(BLOCK);
		write(new int[]{ SUB, SUB, 0, SUB, 0, SUB });
		write(0, 6);

        writeVarLength(BLOCK);
		write(0, 2);
		writeVarLength(realDurationInSamples);
		writeVarLength(0);
		writeVarLength(SUB);
		writeVarLength(reservedDurationInSamples);
		writeVarLength(frequency / frequencyDiv);
		write(0, 2);
		writeVarLength(SUB);
		write(0, 2);
		writeVarLength(1);
		write(0, 3);
		writeId(0, 0);

		writeVarLength(BLOCK);
		write(new int[]{ (int)realDurationInSamples, (int)realDurationInSamples, (int)realDurationInSamples });
		writeVarLength(0);
		writeVarLength(SUB);
		writeVarLength(0);

		writeVarLength(BLOCK);
		writeVarLength(0);

		writeVarLength(BLOCK);
		write(SUB, 4);
		writeVarLength(0);

		writeVarLength(BLOCK);
    	writeVarLength(frequency);
		write(0, 3);
		writeVarLength(1);
		write(0, 3);
		writeId(0, 0);
		write(new int[]{ 0, 1, 1, 0, 1, 0x13 });
		writeVarLength(SUB);

		writeVarLength(BLOCK);
		writeVarLength(0);
		writeVarLength(realDurationInSamples);
		write(0, 2);
		writeVarLength(numChannels);
		write(new int[]{ 1, 0, 1 });
	}

	private void writeChannelData(int ch, List<Long> chDeltas, int chLastState, int lastRecord, boolean useLongDeltas) throws IOException {
    	int numEdges = chDeltas.size();
    	if (numEdges == 0)
    		lastRecord = 0;
    	writeVarLength(CHANNEL_BLOCK);
    	// channel#0 is somehow special...
    	if (ch == 0) {
			writeVarLength(SUB);
			writeVarLength(BLOCK);
		}

		writeVarLength(ch + 1);
		writeVarLength(0);
		writeVarLength(realDurationInSamples);
		writeVarLength(1);
		writeVarLength(lastRecord);

		// todo: why do we convert from
		int numSamplesLeft = (int)(realDurationInSamples - lastRecord);
		writeVarLength(numSamplesLeft);

		writeVarLength(chLastState);

		int chFlag = (numEdges == 0) ? FLAG_EMPTY : (useLongDeltas ? FLAG_NOTEMPTY_LONG : FLAG_NOTEMPTY);
		writeVarLength(chFlag);

		if (ch == 0) {
			writeVarLength(0);
			writeVarLength(BLOCK);
			write(0, 11);
			if (useLongDeltas) {
				writeVarLength(BLOCK);
				write(0, 6);
			}
			writeVarLength(BLOCK);
		} else {
			write(0, 10);
			if (useLongDeltas) {
				write(0, 5);
			}
		}

		writeVarLength(numEdges);
		writeVarLength(0);
		writeVarLength(numEdges);
		writeVarLength(0);
		writeVarLength(numEdges);

		writeEdges(chDeltas, useLongDeltas);

		if (ch == 0) {
			writeVarLength(BLOCK);
			write(0, 6);
			if (!useLongDeltas) {
				writeVarLength(BLOCK);
				write(0, 6);
			}
			writeVarLength(BLOCK);
		} else {
			write(0, 4);
			if (!useLongDeltas) {
				write(0, 5);
			}
		}

		if (numEdges == 0) {
			write(0, 5);
			return;
		}

		writeVarLength(1);
		writeVarLength(0);
		writeVarLength(1);
		writeVarLength(0);
		writeVarLength(1);
    	write(0, 16);

    	writeRaw(0xFF, 8);
		writeRaw(chFlag, 1);
	   	writeRaw(0x00, 7);
    }

	private void writeEdges(List<Long> chDeltas, boolean useLongDeltas) throws IOException {
		for (long d : chDeltas) {
			// set 16-bit 'sign' flag
			if (!useLongDeltas && (d & SIGN_FLAG) == SIGN_FLAG)
				d = (d & 0x7fff) | (SIGN_FLAG >> 16);
			writeByte((byte)(d & 0xff));
			writeByte((byte)((d >> 8) & 0xff));
			if (useLongDeltas) {
				writeByte((byte)((d >> 16) & 0xff));
				writeByte((byte)((d >> 24) & 0xff));
			}
		}
		writeByte(0x00);
	}

	private void writeByte(int i) throws IOException {
		stream.writeByte(i);
	}

	private void writeChannelDataFooter() throws IOException {
    	write(0, 3);
    	writeVarLength(1);
    	writeVarLength(1);
    	writeVarLength(0);
    	writeVarLength(numChannels);
    }

    @Override
	protected void writeFooter() throws IOException {
		if (stream == null)
			return;
		System.out.println("Writing " + eventsBuffer.size() + " event(s)");
		writeEvents(eventsBuffer);
        writeVarLength(BLOCK);
        for (int i = 0; i < numChannels; i++) {
        	writeId(i, 1);
        }
        writeVarLength(1);
		writeId(numChannels, 0x15);
        for (int i = 0; i < numChannels; i++) {
        	writeId(i, 1);
        }
        writeVarLength(1);
        writeVarLength(0);
        writeVarLength(frequency);
		write(0, 16);
        writeVarLength(0x01);
        writeVarLength(0x23); // ???
        writeVarLength(SUB);

        writeVarLength(BLOCK);
        writeVarLength(numChannels + 1);
        writeVarLength(0);
        writeVarLength(0xFFFFFFFFFFFFFFFFL);
        writeVarLength(0xFFFFFFFFL);
        writeVarLength(1);
        write(0, 3);

        writeVarLength(BLOCK);
        writeVarLength(0);

        writeVarLength(BLOCK);
        writeVarLength(0);
        writeDouble(1.0);
        writeVarLength(SUB);
		write(0, 6);
		writeVarLength(1);
		write(0, 4);

        writeVarLength(1);
        writeVarLength(0x29);  // ???
        writeVarLength(SUB);

        writeTimingMarker();

        stream.flush();
		System.out.println("writeFooter " + fileName);
    }

    private void writeTimingMarker() throws IOException {
    	writeVarLength(BLOCK);
    	writeVarLength(numChannels + 2);
    	write(0, 4);
    	write("Timing Marker Pair");
    	write("A1");
    	write("A2");
    	write(0, 2);
    	writeVarLength(SUB);
    	write(0, 9);
    }

    private void writeId(int i1, int i2) throws IOException {
		writeVarLength((numChannels == 4) ? LOGIC4 : LOGIC8);
		writeVarLength(i1);
		writeVarLength(i2);
    }

    ///////////////////////////////////////////////////////////////////////

    private void write(int value, int num) throws IOException {
    	for (int i = 0; i < num; i++)
    		writeVarLength(value);
    }

	private void write(int[] values) throws IOException {
		for (int value : values)
			writeVarLength(value);
	}

    // This is the main secret of this format! :)
    private void writeVarLength(long value) throws IOException {
		stream.writeVarLength(value);
	}

    private void writeDouble(double value) throws IOException {
		if (value == 0.0) {
			writeByte(0);
		} else {
			writeByte(8);
			// doubles are saved little-endian, sorry Java :)
			ByteBuffer bb = ByteBuffer.allocate(8);
			bb.order(ByteOrder.LITTLE_ENDIAN);
	        bb.putDouble(value);
	        bb.rewind();
	        for (int i = 0; i < 8; i++) {
	        	writeByte(bb.get());
	        }
		}
    }

    private void write(String str) throws IOException {
   		writeVarLength(str.length());
    	for (char c : str.toCharArray()) {
    		writeByte(c);
    	}
	}

	private void writeRaw(int value, int num) throws IOException {
    	for (int i = 0; i < num; i++) {
    		writeByte(value);
    	}
    }
}
