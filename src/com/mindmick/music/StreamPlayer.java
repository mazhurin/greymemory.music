/*
 * Copyright(c) 2015 Mindmick Corp. to present
 * Anton Mazhurin & Nawwaf Kharma
 */
package com.mindmick.music;

import com.drkharma.vmf.RelativeNote;
import com.drkharma.vmf.RelativeVMHeader;
import jm.music.data.Note;
import jm.music.data.Part;
import jm.music.data.Phrase;
import jm.music.data.Score;
import jm.util.Play;

import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.MidiUnavailableException;
import org.jfugue.realtime.RealtimePlayer;

/**
 *
 * @author alm
 */
public class StreamPlayer implements Runnable{
    /**
     * The queue containing notes to play.
     */
    private Queue<RelativeNote> noteQueue;

    /**
     * The midi code of the last note which was played.
     */
    private int pitch;

    /**
     * The duration of the last note which was played.
     */
    private double lastDuration;

    /**
     * The length of a tick in milliseconds.
     */
    private double tickLength = 40;

    /**
     * The tempo of the piece.
     */
    //private int tempo;


    public StreamPlayer() {
        this.noteQueue = new LinkedList<>();
    }
    
    /**
     * Initializes the player with the header of a new piece.
     * If the player is running, it is stopped and the queue is cleared.
     * @param header The header of the piece to play.
     */
    public synchronized void init(int pitch) {
        this.noteQueue.clear();

        // Load the reference as the last note to prepare the context.
        //this.pitch = (header.getReferenceOctave() * 12) + header.getReferencePitchClass().getPitchClassCode();
        this.pitch = pitch;

        //this.tempo = header.getMetronomeMarkings().get(0).getQuarterBPM();
        
        add_time = -1;
        current_tick = 0;
    }

    /**
     * Adds a new note to the queue.
     * @param note The note to add to the queue.
     */
    public synchronized void enqueueNote(RelativeNote currentNote) {
        //this.noteQueue.add(note);
        pitch += currentNote.getPitchDelta();
        
        org.jfugue.theory.Note note = new org.jfugue.theory.Note(pitch);

        if(add_time < 0){
            add_time = 0;
        } else{
            add_time = (int) (add_time + currentNote.getOffset()/tickLength);
        }

        if(add_time >= startNote.length) 
            add_time = add_time - startNote.length;

        //note.setDuration(currentNote.getDuration()*tickLength/1000.0);
        
        startNote[add_time] = note;

        int stop_time = (int) (add_time + currentNote.getDuration()/tickLength);
        if(stop_time >= stopNote.length) 
            stop_time = stop_time - stopNote.length;
        stopNote[stop_time] = note;
    }

    private Thread play_thread;
    private org.jfugue.theory.Note[] startNote = new org.jfugue.theory.Note[1000000];
    private org.jfugue.theory.Note[] stopNote = new org.jfugue.theory.Note[1000000];
    private int add_time;
    
    /**
     * Starts the player.
     */
    public void start() throws MidiUnavailableException, InterruptedException {
        
        play_thread = new Thread(this);
        play_thread.start();

        /*
        RealtimePlayer player = new RealtimePlayer();
        
        int tick = (int) (1000*tickLength) / 2;
        
        org.jfugue.theory.Note[] startNote = new org.jfugue.theory.Note[1000000];
        org.jfugue.theory.Note[] stopNote = new org.jfugue.theory.Note[1000000];
        
        int time = -1;
        while (!noteQueue.isEmpty()) {
            RelativeNote currentNote = noteQueue.remove();
            pitch += currentNote.getPitchDelta();
            org.jfugue.theory.Note note = new org.jfugue.theory.Note(pitch);
            
            if(time < 0){
                time = 0;
            } else{
                time = (int) (time + currentNote.getOffset());
            }
            
            if(time >= startNote.length) break;
            
            startNote[time] = note;

            int stop_time = (int) (time + currentNote.getDuration());
            if(stop_time >= stopNote.length) break;
            stopNote[stop_time] = note;
        }
        
        for(int i = 0; i < time; i++){
            if(startNote[i] != null)
               player.startNote(startNote[i]);
            if(stopNote[i] != null)
               player.stopNote(stopNote[i]);
            Thread.sleep(tick);
        }
        */

    }

    /**
     * Stops the player.
     */
    public void stop() {
        play_thread.interrupt();
    }

    protected synchronized void tick(RealtimePlayer player){
        if(add_time < 0){
            return;
        }
        
        if(current_tick != add_time){
            if(startNote[current_tick] != null)
               player.startNote(startNote[current_tick]);
            
            if(stopNote[current_tick] != null)
               player.stopNote(stopNote[current_tick]);
            
            current_tick++;
            if(current_tick == startNote.length)
                current_tick = 0;
        }
    }
    
    private int current_tick;
    @Override
    public void run() {
        try {
            RealtimePlayer player = new RealtimePlayer();

            current_tick = 0;
            while(true){
                tick(player);
                Thread.sleep((long) tickLength);
            }
        } catch (InterruptedException e) {
        } catch (MidiUnavailableException ex) {
            Logger.getLogger(StreamPlayer.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
}
