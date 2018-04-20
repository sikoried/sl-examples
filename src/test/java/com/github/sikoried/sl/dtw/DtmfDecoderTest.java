package com.github.sikoried.sl.dtw;

import com.github.sikoried.jstk.exceptions.MalformedParameterStringException;
import com.github.sikoried.jstk.framed.DTMF;
import com.github.sikoried.jstk.framed.FFT;
import com.github.sikoried.jstk.framed.Window;
import com.github.sikoried.jstk.sampled.AudioFileReader;
import com.github.sikoried.jstk.sampled.AudioSource;
import com.github.sikoried.jstk.sampled.RawAudioFormat;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class DtmfDecoderTest {
	static final String sWindow = "hamm,25,10";

	@Test
	void testDecode() throws MalformedParameterStringException, IOException, UnsupportedAudioFileException {
		ClassLoader classLoader = DtmfDecoder.class.getClassLoader();
		String inFile = classLoader.getResource("audiocheck.net_dtmf_0815.wav").getFile();

		AudioSource as = new AudioFileReader(inFile, RawAudioFormat.create("f:"+inFile), true);
		Window w = Window.create(as, sWindow);
		FFT fft = new FFT(w);
		DTMF fs = new DTMF(fft);

		assertEquals("_0000000000__________8888888888__________1111111111__________5555555555__________", DtmfDecoder.decode(fs, false));


	}

	@Test
	void testDecodeFolded() throws MalformedParameterStringException, IOException, UnsupportedAudioFileException {
		ClassLoader classLoader = DtmfDecoder.class.getClassLoader();
		String inFile = classLoader.getResource("audiocheck.net_dtmf_0815.wav").getFile();
		AudioSource as = new AudioFileReader(inFile, RawAudioFormat.create("f:"+inFile), true);
		Window w = Window.create(as, sWindow);
		FFT fft = new FFT(w);
		DTMF fs = new DTMF(fft);
		assertEquals("0815", DtmfDecoder.decode(fs, true));
	}
}