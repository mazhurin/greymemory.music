/*
 * Copyright(c) 2015 Mindmick Corp. to present
 * Anton Mazhurin & Nawwaf Kharma
 */
package com.mindmick.music;

import com.drkharma.vmf.RelativeNote;
import com.drkharma.vmf.RelativeVMHeader;
import com.mindmick.greymemory.core.SliderRead;
import static java.lang.Math.abs;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice; 
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Track;
import javax.sound.midi.Transmitter;
import org.jfugue.realtime.RealtimePlayer;
import org.jfugue.theory.Note;

/**
 *
 * @author alm
 */
public class MusicMemory  implements Runnable {

    public MusicMemory() {
        this.midi_input_buffer = new ArrayList<>();
        this.midi_input = new ArrayList<>();
        this.keyboard_message_received = false;
    }

    private class MidiNote{
        public int pitch;
        public long duration;
        public boolean complete;
        public long timestamp;
        
        public MidiNote(int pitch, long timestamp){
            this.pitch = pitch;
            this.timestamp = timestamp;
            this.complete = false;
        }
    }
    
    private class MidiReceiver implements Receiver{
        private MusicMemory music_memory;
        public MidiReceiver(MusicMemory music_memory){
            this.music_memory = music_memory;
        }
        
        @Override
        public void send(MidiMessage message, long timeStamp) {
            music_memory.OnMidi(message, timeStamp);
        }

        @Override
        public void close() {

        }
    
    }
    
    private boolean keyboard_message_received;
    //private Queue<> q = new Queue<Integer>();
    
    private ArrayList<MidiNote> midi_input_buffer;
    private ArrayList<MidiNote> midi_input;
    
    protected synchronized void add_midi_input(MidiNote note){
        midi_input.add(note);
    }
    protected synchronized ArrayList<MidiNote> get_midi_input(){
        if(midi_input.size() == 0)
            return null;
        ArrayList<MidiNote> result = midi_input;
        midi_input = new ArrayList<MidiNote>();
        return result;
    }
    
    protected void OnMidi(MidiMessage message, long timeStamp){
        keyboard_message_received = true;
        
        byte[] b = message.getMessage();
        //System.out.println("midi received" + msg);
        //System.out.println("midi 
        int pitch = b[1];
        Note note = new Note(pitch);
        //byte on_off = (byte) abs(b[0] >> 4);

        timeStamp /= 1000;
                
        if(b[2] > 0){
            // start the input
            player.startNote(note);
            
            // add the input to midi notes buffer
            midi_input_buffer.add(new MidiNote(pitch, timeStamp));
        } else {
            //System.out.printf("MIDI  pitch = %d\n", pitch);
            player.stopNote(note);
            
            // find the note in the input and complete 
            for(int i = 0; i < midi_input_buffer.size(); i++){
                MidiNote n = midi_input_buffer.get(i);
                if(n.pitch == pitch){
                    n.duration = timeStamp - n.timestamp;
                    n.complete = true;
                    break;
                }
            }
            
            // add the completed notes to midi_input
            int num_completed = 0;
            for(int i = 0; i < midi_input_buffer.size(); i++){
                MidiNote n = midi_input_buffer.get(i);
                if(n.complete){
                    add_midi_input(n);
                    num_completed++;
                } else
                    break;
            }
            
            for(int i = num_completed-1; i >= 0; i--){
                midi_input_buffer.remove(i);
            }
            
            if(midi_input_buffer.size() > 20)
                midi_input_buffer.clear();
            
        }
    }
    
   

    MidiDevice midi_device;
    Sequence sequence;
    RealtimePlayer player;
    
    public void disconnect_MIDI_device(){
        midi_device.close();
    }
    
    public boolean connect_MIDI_device(MidiDevice.Info info) throws MidiUnavailableException, InvalidMidiDataException, InterruptedException{
        midi_input_buffer.clear();
        
        midi_device = MidiSystem.getMidiDevice(info); 
        midi_device.open();

        Sequencer sequencer = MidiSystem.getSequencer();
        Transmitter transmitter;

        // Open a connection to the default sequencer (as specified by MidiSystem)
        sequencer.open();
        // Get the transmitter class from your input device
        transmitter = midi_device.getTransmitter();
        // Get the receiver class from your sequencer
        //receiver = sequencer.getReceiver();
        // Bind the transmitter to the receiver so the receiver gets input from the transmitter
        transmitter.setReceiver(new MidiReceiver(this));

        // Create a new sequence
        Sequence seq = new Sequence(Sequence.SMPTE_25, 25);
        // And of course a track to record the input on
        Track currentTrack = seq.createTrack();
        // Do some sequencer settings
        sequencer.setSequence(seq);
        sequencer.setTickPosition(0);
        sequencer.recordEnable(currentTrack, -1);
        
        keyboard_message_received = false;
        // And start recording
        sequencer.startRecording();            
        
        for(int i = 0; i < 5; i++){
            Thread.sleep(1000);
            if(keyboard_message_received)
               break;
        }
        if(keyboard_message_received){
        }
        return keyboard_message_received;
    }
            
    @Override
    public void run() {
        System.out.printf("Play something on the MIDI keyboard...\n");
        com.mindmick.music.StreamPlayer stream_player = new com.mindmick.music.StreamPlayer();
        
        //stream_player.init((header.getReferenceOctave() * 12) + header.getReferencePitchClass().getPitchClassCode(), 
        //        header.getTickValue().doubleValue());
        
        // clear midi buffer
        get_midi_input();
        
        try {
            stream_player.start();
            SliderRead predictor = new SliderRead(individual.xdm);
            double data[] = new double[3];
            
            long current_timestamp = 0;
            int current_pitch = 0;
            
            SliderRead.FutureSample prediction = null;
            boolean predict = false;
            while(true){
                ArrayList<MidiNote> midi = get_midi_input();
                if(midi == null && predict){
                    try{
                        int i = 0;
                        while(true){
                            prediction = predictor.predict();
                            if(prediction == null){
                                predict = false;
                                break;
                            }
                            
                            System.out.printf("%d, pitch = %f, offset = %f, duration = %f\n",i, 
                                    prediction.data[2], prediction.data[1], prediction.data[0]);
                          
                            RelativeNote noteNext;
                            noteNext = new RelativeNote(
                                    (int)prediction.data[1],
                                    (int)prediction.data[0],
                                    (int)prediction.data[2]);
                            stream_player.enqueueNote(noteNext);
                            
                            predictor.process(prediction.data);
                            //prediction = predictor.predict();

                            current_timestamp = 0;
                            i++;
                            if(i > 40){
                                midi = get_midi_input();
                                if(midi != null)
                                    break;
                            }
                        }
                    }
                    catch (Exception ex) {
                        Logger.getLogger(MusicMemory.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
                if(midi == null){
                    Thread.sleep(30);
                    continue;
                }
                
                for(MidiNote note: midi){
                    predict = true;
                    if(/*current_timestamp == 0 ||*/ note.timestamp - current_timestamp > 1000){
                        predictor = new SliderRead(individual.xdm);
                        current_pitch = note.pitch;
                        current_timestamp = note.timestamp;
                        stream_player.init(current_pitch);
                    }
                    
                    data[0] = note.duration;
                    data[1] = note.timestamp - current_timestamp;
                    data[2] = note.pitch - current_pitch;
                    
                    current_pitch = note.pitch;
                    current_timestamp = note.timestamp;
                    
                    try {
                        predictor.process(data);
                    } catch (Exception ex) {
                        Logger.getLogger(MusicMemory.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        } catch (InterruptedException e) {
        } catch (MidiUnavailableException ex) {
            Logger.getLogger(MusicMemory.class.getName()).log(Level.SEVERE, null, ex);
        } 
        
        stream_player.stop();
    }

   
    IndividualMusic individual;
    public void start(IndividualMusic individual)  {
        this.individual = individual;
        Scanner in = new Scanner(System.in);
        
        try{
            player = new RealtimePlayer();
            boolean connected = false;

            while(!connected){
                try{
                    // check MIDI keyboard connection
                    System.out.println("Available MIDI devices:");
                    MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
                    for (int i = 0; i < infos.length; i++) {
                        System.out.println(Integer.toString(i+1) + 
                                ":" + infos[i].getName() + "," + infos[i].getVendor());
                    }
                    System.out.println("q: quite");
                    System.out.printf("Enter device 1..%d:\n", infos.length);
                    String s = in.nextLine();
                    if(s.equals("q")) break;
                    int device_index = Integer.parseInt(s) - 1;
                    System.out.printf("MIDI device: %s:\n", infos[device_index].getName());

                    System.out.println("\nPress a key on the MIDI keyboard...");

                    connected = connect_MIDI_device(infos[device_index]);
                    if(connected){
                        System.out.println("SUCCESS! Device connected.");
                        break;
                    }
                    else{
                        disconnect_MIDI_device();
                        System.out.println("Device is not responding. Try another...");
                    }

                } catch(MidiUnavailableException ex){
                    //ex.printStackTrace();
                }catch(InvalidMidiDataException ex){
                    //ex.printStackTrace();
                } catch(InterruptedException ex){
                    //ex.printStackTrace();
                } catch(Exception ex){
                    ex.printStackTrace();
                }
            }

//            while(true){
                Thread play_thread;
                play_thread = new Thread(this);
                play_thread.start();

                String s = in.nextLine();
                play_thread.interrupt();
  //          }

            disconnect_MIDI_device();
            System.out.printf("Stopped.\n");            
            
        } catch (MidiUnavailableException ex){
            ex.printStackTrace();
        }
    }

}
