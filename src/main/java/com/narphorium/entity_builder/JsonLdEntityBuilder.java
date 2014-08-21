package com.narphorium.entity_builder;

import com.mongodb.hadoop.io.BSONWritable;
import com.mongodb.hadoop.io.MongoUpdateWritable;
import com.mongodb.hadoop.mapred.MongoOutputFormat;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.KeyValueTextInputFormat;
import org.apache.hadoop.mapred.TextInputFormat;
import org.apache.hadoop.mapred.TextOutputFormat;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.mapred.jobcontrol.JobControl;
import org.apache.hadoop.mapred.lib.IdentityMapper;

import java.io.File;
import java.io.IOException;

/**
 * TODO: Insert description here. (generated by simister)
 */
public class JsonLdEntityBuilder {

  /**
   * @param args
   */
  public static void main(String[] args) {
    
    File inputFile = new File(args[0]);
    File frameFile = new File(args[1]);
    File tempDir = new File(args[2]);
    String dbPath = args[3];

    try {
      JobControl jobControl = new JobControl("jsonld-entities");

      JobConf defaultConf = new JobConf();

      // Map the triples into JSON-LD fragments
      JobConf initialLoadConf = new JobConf(defaultConf);
      initialLoadConf.setInt("rank", 0);
      initialLoadConf.setStrings("frame-file", frameFile.toString());
      initialLoadConf.setMapperClass(TripleMapper.class);
      initialLoadConf.setReducerClass(EntityReducer.class);
      initialLoadConf.setInputFormat(TextInputFormat.class);
      initialLoadConf.setOutputFormat(TextOutputFormat.class);
      initialLoadConf.setMapOutputKeyClass(Text.class);
      initialLoadConf.setMapOutputValueClass(Text.class);
      initialLoadConf.setOutputKeyClass(Text.class);
      initialLoadConf.setOutputValueClass(Text.class);
      FileInputFormat.setInputPaths(initialLoadConf, new Path(inputFile.toString()));
      Path outputPath = new Path(tempDir.toString() + "/stage0");
      FileOutputFormat.setOutputPath(initialLoadConf, outputPath);
      Path prevOutput = outputPath;
      Job initialLoad = new Job(initialLoadConf);
      jobControl.addJob(initialLoad);

      // Aggregate JSON-LD fragments into nested structure
      EntityFrame entityFrame = new EntityFrame();
      entityFrame.parse(frameFile);
      Job prevJob = initialLoad;
      for (int rank = 1; rank <= entityFrame.getMaxRank(); rank++) {
        JobConf conf = new JobConf(defaultConf);
        conf.setInt("rank", rank);
        conf.setStrings("frame-file", frameFile.toString());
        conf.setMapperClass(IdentityMapper.class);
        conf.setReducerClass(EntityReducer.class);
        conf.setInputFormat(KeyValueTextInputFormat.class);
        conf.setOutputFormat(TextOutputFormat.class);
        conf.setMapOutputKeyClass(Text.class);
        conf.setMapOutputValueClass(Text.class);
        conf.setOutputKeyClass(Text.class);
        conf.setOutputValueClass(Text.class);
        FileInputFormat.setInputPaths(conf, prevOutput);
        outputPath = new Path(tempDir.toString() + "/stage" + rank);
        FileOutputFormat.setOutputPath(conf, outputPath);
        prevOutput = outputPath;
        Job buildEntityJob = new Job(conf);
        jobControl.addJob(buildEntityJob);
        buildEntityJob.addDependingJob(prevJob);
        prevJob = buildEntityJob;
      }

      // Frame nested data
      JobConf frameConf = new JobConf(defaultConf);
      frameConf.setStrings("frame-file", frameFile.toString());
      frameConf.setMapperClass(IdentityMapper.class);
      frameConf.setReducerClass(EntityFrameReducer.class);
      frameConf.setInputFormat(KeyValueTextInputFormat.class);
      frameConf.setOutputFormat(MongoOutputFormat.class);
      frameConf.set("mongo.output.uri", dbPath);
      frameConf.set("stream.io.identifier.resolver.class", "com.mongodb.hadoop.mapred.MongoOutputFormat");
      frameConf.setMapOutputKeyClass(Text.class);
      frameConf.setMapOutputValueClass(Text.class);
      frameConf.setOutputKeyClass(NullWritable.class);
      frameConf.setOutputValueClass(MongoUpdateWritable.class);
      FileInputFormat.setInputPaths(frameConf, prevOutput);
      Job frameEntitiesJob = new Job(frameConf);
      jobControl.addJob(frameEntitiesJob);
      frameEntitiesJob.addDependingJob(prevJob);

      FileSystem fs = FileSystem.get(defaultConf);
      fs.delete(new Path(tempDir.toString()), true);

      // Run pipeline
      jobControl.run();

    } catch (IOException e) {
      // TODO(simister): Auto-generated catch block
      e.printStackTrace();
    }
  }

}
