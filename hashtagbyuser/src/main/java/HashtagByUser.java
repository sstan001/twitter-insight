import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.SequenceFileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;

import java.io.IOException;
import java.util.Arrays;
import java.util.SortedSet;
import java.util.TreeSet;

import static java.lang.String.CASE_INSENSITIVE_ORDER;

public class HashtagByUser {

    public static class HashtagByUserMapper extends Mapper<LongWritable, TweetWritable, Text, Text> {
        private final Gson gson = new GsonBuilder().create();

        @Override
        public void map(LongWritable key, TweetWritable value, Context context) throws IOException, InterruptedException {
            String[] hashtags = value.hashtags;
            String jsonArray = gson.toJson(hashtags);
            context.write(new Text(String.format("(%s, %d)", value.userName, value.userId)), new Text(jsonArray));
        }
    }

    public static class HashtagByUserReducer extends Reducer<Text, Text, Text, Text> {
        private final Gson gson = new GsonBuilder().create();

        @Override
        protected void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            SortedSet<String> sortedSet = new TreeSet<>(CASE_INSENSITIVE_ORDER);

            // For each tweet of a user.
            for (Text jsonArray : values) {
                String[] hashtags = gson.fromJson(jsonArray.toString(), String[].class);

                // For each hashtags of a tweet.
                sortedSet.addAll(Arrays.asList(hashtags));
            }

            context.write(new Text(key.toString()), new Text(gson.toJson(sortedSet)));
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();

        Job job = Job.getInstance(conf, "HashtagByUser");
        job.setJarByClass(HashtagByUser.class);
        job.setMapperClass(HashtagByUserMapper.class);
        job.setCombinerClass(HashtagByUserReducer.class);
        job.setReducerClass(HashtagByUserReducer.class);
        job.setNumReduceTasks(15);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        job.setInputFormatClass(SequenceFileInputFormat.class);
        job.setOutputFormatClass(SequenceFileOutputFormat.class);

        try {
            FileInputFormat.addInputPath(job, new Path(args[0]));
            FileOutputFormat.setOutputPath(job, new Path(args[1]));
        } catch (Exception e) {
            System.out.println("Bad arguments : waiting for 2 arguments [inputURI] [outputURI]");
            return;
        }

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }

}
