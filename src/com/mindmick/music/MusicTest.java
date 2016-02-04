/*
 * Copyright(c) 2015 Mindmick Corp. to present
 * Anton Mazhurin & Nawwaf Kharma
 */
package com.mindmick.music;

import com.drkharma.vmf.AbbreviatedVectorMusic;
import com.drkharma.vmf.MetronomeMarking;
import com.drkharma.vmf.RelativeNote;
import com.drkharma.vmf.RelativeVMHeader;
import com.drkharma.vmf.adapter.VMFAdapter;
import com.drkharma.vmf.adapter.synthesis.AbbreviatedVMFPlayer;
import com.drkharma.vmf.parser.exception.TimeSignatureMissingException;
import com.mindmick.greymemory.core.SliderRead;
import com.mindmick.greymemory.core.SliderRead.FutureSample;
import com.mindmick.greymemory.core.Sample;
import com.mindmick.greymemory.core.SliderWrite; 
import com.mindmick.music.IndividualMusic;
import com.mindmick.music.MusicMemory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

import org.jfugue.player.Player;
import jm.util.Play;

/**
 *
 * @author amazhurin
 */
public class MusicTest {
    
    private void play(File file) throws MidiUnavailableException, IOException, TimeSignatureMissingException, InterruptedException{
        VMFAdapter adapter;
        adapter = new VMFAdapter(file);
        AbbreviatedVectorMusic music = adapter.abbreviate();
        
        com.mindmick.music.StreamPlayer stream_player = new com.mindmick.music.StreamPlayer();
       
        RelativeVMHeader header = music.getHeader();
        
        int music_tick = (int)(header.getTickValue().doubleValue()*1000f)/2;
        stream_player.init((header.getReferenceOctave() * 12) + header.getReferencePitchClass().getPitchClassCode());

        for(RelativeNote note: music.getNotes()){
            RelativeNote noteXDM = new RelativeNote(note.getOffset()*music_tick,
                note.getDuration()*music_tick, note.getPitchDelta());
            
            stream_player.enqueueNote(noteXDM);
        }
        
        System.out.printf("Playing(press enter to stop)...\n");
        stream_player.start();
        
        Scanner in = new Scanner(System.in);
        String s = in.nextLine();
        stream_player.stop();
    }
    private void play(String path) throws MidiUnavailableException, IOException, TimeSignatureMissingException, InterruptedException{
        File file = new File(path);
        play(file);
    }
    
    private void xdm_play(IndividualMusic ind, int num_songs, ArrayList<File> files) throws IOException, TimeSignatureMissingException, Exception{
        while(true){
            try{
                Scanner in = new Scanner(System.in);
                System.out.printf("Enter music index 1..%d:\n", num_songs);
                String s = in.nextLine();
                if(s.startsWith("q"))
                    break;
                int index = Integer.parseInt(s) - 1;
                if(index >= 0 && index < files.size()){
                    VMFAdapter adapter;
                    adapter = new VMFAdapter(files.get(index));
                    AbbreviatedVectorMusic music = adapter.abbreviate();

                    //System.out.printf("Enter note index 0..%d:\n", music.getNotes().size()-1);
                    //s = in.nextLine();
                    //if(s.equals("q"))
                    //    break;
                    //int note_index = Integer.parseInt(s);
                    int note_index = 0;
                            
                    int i = 0;
                    SliderRead predictor = new SliderRead(ind.xdm);
                    double data[] = new double[3];

                    int music_tick = (int)(music.getHeader().getTickValue().doubleValue() * 1000)/2;

                    // find the music in xdm
                    com.mindmick.music.StreamPlayer stream_player = new com.mindmick.music.StreamPlayer();
                    RelativeVMHeader header = music.getHeader();
                    stream_player.init((header.getReferenceOctave() * 12) + header.getReferencePitchClass().getPitchClassCode());

                    FutureSample prediction = null;
                    for(RelativeNote note: music.getNotes()){
                        if(i < note_index)
                            continue;
                        RelativeNote noteXDM = new RelativeNote(
                            note.getOffset()*music_tick,
                            note.getDuration()*music_tick, 
                            note.getPitchDelta());
                                
                        // play the note
                        stream_player.enqueueNote(noteXDM);
                        
                        // process with predictor
                        data[0] = noteXDM.getDuration();
                        data[1] = noteXDM.getOffset();;
                        data[2] = note.getPitchDelta();
                        predictor.process(data);
                        prediction = predictor.predict();
                        if(prediction != null){
                            break;
                        }
                    }

                    System.out.printf("Playing(press enter to stop)...\n");
                    stream_player.start();

                    // start from xdm
                    int num_played = 0;
                    while(prediction != null){
                        RelativeNote noteNext;
                        noteNext = new RelativeNote(
                                (int)prediction.data[1],
                                (int)prediction.data[0],
                                (int)prediction.data[2]);
                        stream_player.enqueueNote(noteNext);
                        num_played++;

                        predictor.process(prediction.data);
                        prediction = predictor.predict();

                        if(num_played > 100)
                            break;
                    }

                    s = in.nextLine();
                    stream_player.stop();
                }
            } catch (Exception ex){
                ex.printStackTrace();
            } catch (TimeSignatureMissingException exTime){
                exTime.printStackTrace();
            }
        }
    }
    
    private void test_accuracy(IndividualMusic ind, int num_songs, ArrayList<File> files) throws IOException{
        int num_total = 0;
        int num_correct_total = 0;
        int num_correct_meta_total = 0;
        
        Scanner in = new Scanner(System.in);
        System.out.printf("Enter Duration noise(0..300), mls:");
        int duration_noise = in.nextInt();
        System.out.println();
        
        Random rnd = new Random(33);
                
        for(int index = 0; index < num_songs; index++){
            try{
                VMFAdapter adapter;
                adapter = new VMFAdapter(files.get(index));
                AbbreviatedVectorMusic music = adapter.abbreviate();

                // create predictor
                SliderRead predictor = new SliderRead(ind.xdm);
                double data[] = new double[3];

                int num_local = 0;
                int num_correct_local = 0;
                int num_correct_meta_local = 0;

                int music_tick = (int)(music.getHeader().getTickValue().doubleValue() * 1000)/2;

                boolean first_note = true;
                for(RelativeNote note: music.getNotes()){
                    FutureSample prediction = predictor.predict();

                    RelativeNote noteXDM;
                    if(first_note){
                        first_note = false;
                        noteXDM = new RelativeNote(
                                0,
                                note.getDuration()*music_tick, 
                                0);
                    } else {
                        noteXDM = new RelativeNote(
                                note.getOffset()*music_tick,
                                note.getDuration()*music_tick, 
                                note.getPitchDelta());
                    }
                        
                    data[0] = noteXDM.getDuration();
                    if(duration_noise > 0){
                           data[0] = data[0] - duration_noise + rnd.nextInt(2*duration_noise);
                        if(data[0]<0) data[0] = 0;
                    }
                    
                    data[1] = noteXDM.getOffset();;
                    data[2] = note.getPitchDelta();
                    
                    if(prediction != null){
                        num_total++;
                        num_local++;
                        //if(prediction.error == Sample.Error.OK){
                            if(
                               Math.abs(prediction.data[0] - data[0]) < 60 && 
                               Math.abs(prediction.data[1] - data[1]) < 60 && 
                               prediction.data[2] == data[2]){
                                num_correct_total++;                
                                num_correct_local++;                
                            } else {
                                int y =0;
                            }
                            if(prediction.meta_data == index){
                                num_correct_meta_total++;             
                                num_correct_meta_local++;             
                            }
                        //}
                    }
                    predictor.process(data);
                }
                System.out.printf("Music [%d],%s: correct %f%%, meta=%f%%\n", 
                        index,
                        files.get(index).getName(),
                        num_correct_local*100f/num_local,
                        num_correct_meta_local*100f/num_local);
            } catch (Exception ex){
                System.out.printf("Music [%d]: Exception:\n", index);
                ex.printStackTrace();
            } catch (TimeSignatureMissingException exTime){
                System.out.printf("Music [%d]: Exception:\n", index);
                exTime.printStackTrace();
            }
        }

        System.out.printf("Music prediction = %f%%, meta=%f%%\n", 
                num_correct_total*100f/num_total,
                num_correct_meta_total*100f/num_total);
    }

    
    private void test_accuracy_SNR(IndividualMusic ind, int num_songs, 
            ArrayList<File> files, int indexData) throws IOException{
        int num_total = 0;
        int num_correct_total = 0;
        int num_correct_meta_total = 0;
    /*    
        Scanner in = new Scanner(System.in);
        System.out.printf("Enter Duration noise(0..300), mls:");
        int duration_noise = in.nextInt();
        System.out.println();
      */
        BufferedWriter writer = null;
        String channel;
        if(indexData == 0) 
            channel = "duration";
        else if(indexData == 1)
            channel = "offset";
        else
            channel = "pitch";
        
        writer = new BufferedWriter(new FileWriter(new File("accuracy" +
                "_" + channel + "_" + 
                Integer.toString(ind.xdm.param.window.length) + ".csv")));
        
        Random rnd = new Random(33);
        
        float power_signal = 0f;
        float power_noise = 0f;
        
        ArrayList<AbbreviatedVectorMusic> musics = new ArrayList<>();
        
        try{
            for(int index = 0; index < num_songs; index++){
                VMFAdapter adapter;
                adapter = new VMFAdapter(files.get(index));
                AbbreviatedVectorMusic music = adapter.abbreviate();
                musics.add(music);
            }

            float range;
            float step;
            float start;
            if(indexData < 2){
                range = 800;
                step = 20;
                start = 5f;
            } else {
                range = 15;
                step = 0.25f;
                start = 0.05f;
            }
            for(float stddev = start; stddev < range; stddev+=step){
                for(int index = 0; index < num_songs; index++){
                    AbbreviatedVectorMusic music = musics.get(index);

                    // create predictor
                    SliderRead predictor = new SliderRead(ind.xdm);
                    double data[] = new double[3];

                    int num_local = 0;
                    int num_correct_local = 0;
                    int num_correct_meta_local = 0;

                    int music_tick = (int)(music.getHeader().getTickValue().doubleValue() * 1000)/2;

                    boolean first_note = true;
                    int num_notes = music.getNotes().size();
                    for(int n = 0; n < num_notes - ind.xdm.param.window.length -1 ; n++){
                        RelativeNote note = music.getNotes().get(n);
                        FutureSample prediction = predictor.predict();

                        RelativeNote noteXDM;
                        if(first_note){
                            first_note = false;
                            noteXDM = new RelativeNote(
                                    0,
                                    note.getDuration()*music_tick, 
                                    0);
                        } else {
                            noteXDM = new RelativeNote(
                                    note.getOffset()*music_tick,
                                    note.getDuration()*music_tick, 
                                    note.getPitchDelta());
                        }

                        data[0] = noteXDM.getDuration();
                        data[1] = noteXDM.getOffset();
                        data[2] = note.getPitchDelta();

                        float noise = 0;
                        if(stddev > 0){
                            noise = (float) (stddev*rnd.nextGaussian());
                        }
                        data[indexData] += noise;
                        if(indexData < 2 && data[indexData]<0) 
                            data[indexData] = 0;

                        power_signal += data[indexData]*data[indexData];
                        power_noise += noise*noise;

                        if(prediction != null){
                            num_total++;
                            num_local++;
                            //if(prediction.error == Sample.Error.OK){
                                if(
                                   Math.abs(prediction.data[0] - data[0]) < 60 && 
                                   Math.abs(prediction.data[1] - data[1]) < 60 && 
                                   prediction.data[2] == data[2]){
                                    num_correct_total++;                
                                    num_correct_local++;                
                                } else {
                                    int y =0;
                                }
                                if(prediction.meta_data == index){
                                    num_correct_meta_total++;             
                                    num_correct_meta_local++;             
                                } else {
                                    int u = 0;
                                }
                            //}
                        }
                        predictor.process(data);
                    }
                    /*
                    System.out.printf("Music [%d],%s: correct %f%%, meta=%f%%\n", 
                            index,
                            files.get(index).getName(),
                            num_correct_local*100f/num_local,
                            num_correct_meta_local*100f/num_local);*/
                }

                float accuracy = num_correct_total*100f/num_total;
                float meta_accuracy = num_correct_meta_total*100f/num_total;

                float SNR = power_signal/power_noise;

                System.out.printf("stddev = %f, SNR = %f, accuracy = %f%%, meta_accuracy=%f%%\n", 
                        stddev*1.0f,
                        SNR,
                        accuracy,
                        meta_accuracy);

                try {
                    writer.write(Float.toString(stddev) +  "," +
                            Float.toString(SNR) + "," +
                            Float.toString(accuracy) + "," +
                            Float.toString(meta_accuracy) + "\n");
                } catch (IOException ex) {
                    System.out.println(ex.toString());
                }
            }
        } catch (Exception ex){
            ex.printStackTrace();
        } catch (TimeSignatureMissingException exTime){
            exTime.printStackTrace();
        }
        
        
        writer.close();
    }

    private ArrayList<File> remove_duplicates(File[] files){
        ArrayList<File> res = new ArrayList<>();
        
        ArrayList<Long> sizes = new ArrayList<>();
        
        for(int i = 0; i < files.length; i++){
            long size = files[i].length();
            if(sizes.contains(size))
                continue;
            sizes.add(size);
            res.add(files[i]);
        }
            
        return res;
    }
    
    public void run(String vmf_folder) throws Exception {
        try{
            File dir = new File(vmf_folder);
            File[] filesOriginal = dir.listFiles();
            
            ArrayList<File> files = remove_duplicates(filesOriginal);
            
            Scanner in = new Scanner(System.in);
            while(true){
                System.out.printf("1 - Play a song\n");
                System.out.printf("2 - Create XDM storage\n");
                System.out.printf("3 - Play 'Yesterday'\n");
                System.out.printf("4 - Accuracy SNR((multiply windows)\n");
                System.out.printf("q - quit\n");
                String s = in.nextLine();
                if(s.equals("q")) break;
                if(s.equals("1")) {
                    System.out.printf("Enter music index 1..%d:\n", files.size());
                    s = in.nextLine();
                    play(files.get(Integer.parseInt(s)-1));
                } else if(s.equals("3")){
                    play(vmf_folder + "/aaaa_yesterday.vmf");
                } else if(s.equals("4")){
                    System.out.printf("Enter max number of songs 1..%d\n", files.size());
                    s = in.nextLine();
                    int num_songs = Integer.parseInt(s);
                    
                    for(int w = 3; w <=13; w+=2){
                        IndividualMusic ind = new IndividualMusic();
                        ind.genome.get_gene("window").value = w;
                        System.out.printf("Num notes %d : \n", w);
                        if (files != null) {
                            for (int i = 0; i < num_songs; i++) {
                                ind.Write(files.get(i), i);
                                System.out.printf("Storing(%s)...  %2.1f %%\n", 
                                        files.get(i).getName(),
                                        (i+1)*100f/num_songs);
                                if(i >= num_songs) break;
                            }
                        } else {
                            // Handle the case where dir is not really a directory.
                            // Checking dir.isDirectory() above would not be sufficient
                            // to avoid race conditions with another process that deletes
                            // directories.
                            throw new Exception(vmf_folder + " is not a directory.");
                        }  
                        test_accuracy_SNR(ind, num_songs, files, 0); 
                        test_accuracy_SNR(ind, num_songs, files, 1); 
                        test_accuracy_SNR(ind, num_songs, files, 2);
                        
                    }
                } else if(s.equals("2")){
                    System.out.printf("Enter windows size 3..12\n");
                    s = in.nextLine();
                    IndividualMusic ind = new IndividualMusic();
                    ind.genome.get_gene("window").value = Integer.parseInt(s);

                    System.out.printf("Enter max number of songs 1..%d\n", files.size());
                    s = in.nextLine();
                    
                    int num_songs = Integer.parseInt(s);
                    System.out.println("Storing music into XDM...");

                    if (files != null) {
                        for (int i = 0; i < num_songs; i++) {
                            ind.Write(files.get(i), i);
                            System.out.printf("Storing(%s)...  %2.1f %%\n", 
                                    files.get(i).getName(),
                                    (i+1)*100f/num_songs);
                            if(i >= num_songs) break;
                        }
                    } else {
                        // Handle the case where dir is not really a directory.
                        // Checking dir.isDirectory() above would not be sufficient
                        // to avoid race conditions with another process that deletes
                        // directories.
                        throw new Exception(vmf_folder + " is not a directory.");
                    }   
                    System.out.printf("\n");
                    System.out.printf("XDM created(%d songs)\n", num_songs);

                    while(true){
                        System.out.printf("1 - Play a song\n");
                        System.out.printf("2 - Play from XDM\n");
                        System.out.printf("3 - Accuracy\n");
                        System.out.printf("4 - SNR Accuracy \n");
                        System.out.printf("5 - DEMO\n");
                        s = in.nextLine();
                        if(s.equals("q"))
                            break;
                        
                        switch (s){
                            case "1" :
                                System.out.printf("Enter music index 1..%d:\n", files.size());
                                s = in.nextLine();
                                play(files.get(Integer.parseInt(s)-1));
                                break;
                            case "2" : xdm_play(ind, num_songs, files); break;
                            case "3" : test_accuracy(ind, num_songs, files); break;
                            case "4" : 
                                test_accuracy_SNR(ind, num_songs, files, 0); 
                                test_accuracy_SNR(ind, num_songs, files, 1); 
                                test_accuracy_SNR(ind, num_songs, files, 2); 
                                break;
                            case "5" : {
                                MusicMemory demo = new MusicMemory();
                                demo.start(ind);
                            } break;
                        }
                    }
                }
            }
            
        } catch(TimeSignatureMissingException ex){
            ex.printStackTrace();
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
        
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        try{
            com.mindmick.music.MusicTest test;
            test = new com.mindmick.music.MusicTest();
            
            //String current = new java.io.File( "." ).getCanonicalPath();
            test.run("..\\data\\vmf3");
        } catch(Exception ex){
            ex.printStackTrace();
        }

    }
        
}
