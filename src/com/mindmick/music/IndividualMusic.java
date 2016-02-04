/*
 * Copyright(c) 2015 Mindmick Corp. to present
 * Anton Mazhurin & Nawwaf Kharma
 */
package com.mindmick.music;
 
import com.drkharma.vmf.AbbreviatedVectorMusic;
import com.drkharma.vmf.RelativeNote;
import com.drkharma.vmf.adapter.VMFAdapter;
import com.drkharma.vmf.parser.exception.TimeSignatureMissingException;
import com.mindmick.greymemory.core.SliderWrite;
import com.mindmick.greymemory.core.XDM;
import com.mindmick.greymemory.core.XDMParameters;
import com.mindmick.greymemory.evolution.Gene;
import com.mindmick.greymemory.evolution.Individual;
import java.io.File;
import java.io.IOException;

/**
 *
 * @author alm
 */
public class IndividualMusic extends Individual{
    
    public IndividualMusic(){
 /*       
        genome.genes.add(new Gene("window", 2f, 37f, 9f));
        genome.genes.add(new Gene("num_hard_locations", 7f, 100f, 9f));
        genome.genes.add(new Gene("forgetting_rate", 10f, 2000f, 1119000));
        
        genome.genes.add(new Gene("duration_activation_radius", 1, 1, 1));
        genome.genes.add(new Gene("offset_activation_radius", 1, 1, 1));
        genome.genes.add(new Gene("pitch_activation_radius", 1, 1, 1));
        */

        
        genome.genes.add(new Gene("window", 2f, 37f, 9f));
        genome.genes.add(new Gene("num_hard_locations", 7f, 100f, 37f));
        genome.genes.add(new Gene("forgetting_rate", 10f, 2000f, 119999000));

        genome.genes.add(new Gene("duration_activation_radius", 50, 1000, 200));
        genome.genes.add(new Gene("offset_activation_radius", 50, 1000, 200));
        genome.genes.add(new Gene("pitch_activation_radius", 1, 1000, 2));
    }
    
    
    @Override
    public Individual create() {
        Individual individual = new IndividualMusic();
        return individual;
    }

    @Override
    public void calculate_cost() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    public XDM xdm;

    private void create_xdm() throws Exception {
        XDMParameters param;
        param = new XDMParameters();
        
        param.predict = true;
        param.num_channels = 3;
        param.num_channels_prediction = 3;
        param.max_storage_size_in_mb = 9000;
        //param.meta_data_range = 1000;
        param.meta_data_range = 1000;

        param.window = new int[(int)(genome.get_gene("window").value)];
        param.min_num_hard_location = (int)genome.get_gene("num_hard_locations").value;
        param.forgetting_rate = (int)genome.get_gene("forgetting_rate").value;
 
        param.activation_radius = new double[param.num_channels];
        param.resolution = new double[param.num_channels];
        param.prediction_radius = new double[param.num_channels_prediction];
        param.medians = new double[param.num_channels];
        param.classes = new int[1];
        param.classes[0] = 0;
        
        // duration
        int i = 0;
        //param.medians[i] = 10f; 
        param.medians[i] = 300f; 
        param.resolution[i] = 50;
        param.activation_radius[i] = genome.get_gene("duration_activation_radius").value;
        param.prediction_radius[i] = 4500;
        //param.prediction_radius[i] = 20;
        
        // offset
        i++;
        param.medians[i] = 800f; 
        //param.medians[i] = 10f; 
        param.resolution[i] = 50;
        param.activation_radius[i] = genome.get_gene("offset_activation_radius").value;
        param.prediction_radius[i] = 3500;

        
        // pitch
        i++;
        param.medians[i] = 0f; 
        param.activation_radius[i] = genome.get_gene("pitch_activation_radius").value;
        param.prediction_radius[i] = 60;
        
        xdm = new XDM(param);
    }
   
    public void Write(File vmf_file, int id) throws Exception{
        try{
            if(xdm == null){
                create_xdm();
            }
        
            VMFAdapter adapter;
            adapter = new VMFAdapter(vmf_file);

            // read the VMF
            AbbreviatedVectorMusic music = adapter.abbreviate();
            
            org.apache.commons.lang3.math.Fraction fr;
            fr = music.getHeader().getTickValue();
            
            int music_tick = (int)( music.getHeader().getTickValue().doubleValue()*1000f)/2;
            
            // create SliderWrite
            SliderWrite trainer = new SliderWrite(xdm);
            double data[] = new double[3];
            
            boolean first_note = true;
            int i = 0;
            for(RelativeNote note: music.getNotes()){
                if(first_note){
                    data[0] = note.getDuration()*music_tick;
                    data[1] = 0;
                    data[2] = 0;
                    first_note = false;
                } else {
                    data[0] = note.getDuration()*music_tick;
                    data[1] = note.getOffset()*music_tick;
                    data[2] = note.getPitchDelta();
                }
                
                trainer.train(data, id);
            }
        
        } catch(TimeSignatureMissingException ex){
            ex.printStackTrace();
        }
        catch(IOException ex){
            ex.printStackTrace();
        }
        
            
    }
    
}
