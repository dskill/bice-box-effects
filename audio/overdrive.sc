(
    	~rms_bus_input = Bus.control(s, 1);
	~rms_bus_output = Bus.control(s, 1);


    SynthDef(\overdrive, {
        |out = 0,
        // START USER EFFECT CODE
        drive = 0.5, tone = 0.5|
        var sig, distorted, phase, trig, partition;

        // Adjust the playback rate to match the server's sample rate
        var playbackRate = BufSampleRate.kr(~buffer) / SampleRate.ir() / 2;
        sig = PlayBuf.ar(1, ~buffer, BufRateScale.kr(~buffer) * playbackRate, loop: 1);
        distorted = (sig * drive ).tanh();
        //distorted = LPF.ar(distorted, tone.linexp(0, 1, 100, 20000));

        // END USER EFFECT CODE

        // Calculate RMS values
        Out.kr(~rms_out_input, RunningSum.kr(sig.squared, 1024).sqrt / 32);
        Out.kr(~rms_out_output, RunningSum.kr(distorted.squared, 1024).sqrt / 32);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize * 1);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer0, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer1, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, distorted);
    }).add;
    "Effect SynthDef added".postln;

    ~overdrive = Synth(\overdrive);
    "New effect synth created".postln;
)
