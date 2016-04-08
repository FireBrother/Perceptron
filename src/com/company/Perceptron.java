package com.company;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by wuxian on 16/3/24.
 */
public class Perceptron {
    private Logger _logger = Logger.getLogger("main");
    private Feature _model;
    private HashSet<String> _tagset = new HashSet<>();
    private Properties prop = new Properties();
    private int numFeatures = 8;

    final public Feature CreateLocalFeature(String sentence, String tagSeq, int pos) {
        return CreateLocalFeature(sentence.split(" "), tagSeq.split(" "), pos);
    }
    final public Feature CreateLocalFeature(String[] words, String[] tags, int pos) {
        Feature feature = new Feature();
        String[] begin = {"_x-1", "_x-2"};
        String[] end = {"_x+1", "_x+2"};
        String prev1, prev2, succ1, succ2;
        if (pos == 0) {
            prev1 = begin[0];
            prev2 = begin[1];
        }
        else if (pos == 1) {
            prev1 = words[pos - 1];
            prev2 = begin[0];
        }
        else {
            prev1 = words[pos - 1];
            prev2 = words[pos - 2];
        }
        if (pos == words.length - 1) {
            succ1 = end[0];
            succ2 = end[1];
        }
        else if (pos == words.length - 2) {
            succ1 = words[pos + 1];
            succ2 = end[0];
        }
        else {
            succ1 = words[pos + 1];
            succ2 = words[pos + 2];
        }
        String prevTag;
        if (pos == 0) {
            prevTag = "_t-1";
        }
        else {
            prevTag = tags[pos - 1];
        }
        // 修改的同时请修改numFeatures
        feature.add("u1:0_0:", words[pos], tags[pos]);
        feature.add("u2:-1_0:", prev1, tags[pos]);
        feature.add("u3:+1_0:", succ1, tags[pos]);
        feature.add("u4:-1_0_0:", prev1+"_"+words[pos], tags[pos]);
        feature.add("u5:0_1_0:", words[pos]+"_"+succ1, tags[pos]);
        feature.add("u6:0+1_0:", words[pos].substring(0, 1), tags[pos]);
        feature.add("u7:0-1_0:", words[pos].substring(words[pos].length()-1), tags[pos]);
        feature.add("b1:-1_0:", prevTag, tags[pos]);
        return feature;
    }

    final public Feature CreateGlobalFeature(String sentence, String tagSeq) {
        return CreateGlobalFeature(sentence.split(" "), tagSeq.split(" "));
    }
    final public Feature CreateGlobalFeature(String[] words, String[] tags) {
        Feature feature = new Feature(words.length * numFeatures);
        if (words.length != tags.length) {
            _logger.log(Level.SEVERE, String.format("missing word or tag: %s/%s",
                    String.join(" ", words), String.join(" ", tags)));
        }
        for (int i = 0; i < words.length; i++) {
            feature.PlusAssign(CreateLocalFeature(words, tags, i));
        }
        return feature;
    }

    final public void LoadModel(File file) throws FileNotFoundException {
        _logger.log(Level.INFO, String.format("Load model from %s", file.getName()));
        Scanner scanner = new Scanner(new FileInputStream(file));
        String line = scanner.nextLine();
        int tagSetSize = Integer.parseInt(line.split("#")[2]);
        for (int i = 0; i < tagSetSize; i++) {
            _tagset.add(scanner.nextLine());
        }
        line = scanner.nextLine();
        int modelSize = Integer.parseInt(line.split("#")[2]);
        _model = new Feature(modelSize);
        for (int i = 0; i < modelSize; i++) {
            line = scanner.nextLine();
            _model.feature.put(line.split("\t")[0], Double.parseDouble(line.split("\t")[1]));
        }
    }

    final public String[] Tag(String[] words) {
        return Tag(words, _model);
    }
    final public String[] Tag(String[] words, Feature model) {
        if (model == null)
            _logger.log(Level.SEVERE, "Uninitialized model!");
        String[] tagSeq = new String[words.length];
        _Viterbi(words, tagSeq, model);
        return tagSeq;
    }

    final public String Tag(String tokedSentence) {
        return Tag(tokedSentence, _model);
    }
    final public String Tag(String tokedSentence, Feature model) {
        StringBuilder ret = new StringBuilder();
        if (tokedSentence != null){
            String[] words = tokedSentence.split(" ");
            String ts[] = Tag(words, model);
            for (int i = 0; i < ts.length; i++) {
                ret.append(ts[i]);
                ret.append(" ");
            }
        }
        return ret.toString();
    }

    final public String TagTokenizedString(String tokedSentence) {
        StringBuilder ret = new StringBuilder();
        if (tokedSentence != null){
            String[] words = tokedSentence.split(" ");
            String[] ts = Tag(words, _model);
            for (int i = 0; i < ts.length; i++) {
                ret.append(words[i]);
                ret.append('#');
                ret.append(ts[i]);
                ret.append(' ');
            }
        }
        return ret.toString();
    }

    final public boolean Train(File file, double learningRate, int maxIter, double thresh, int numThreads)
            throws IOException, InterruptedException {
        Scanner scanner = new Scanner(new FileInputStream(file));
        scanner.useDelimiter("\n\n");
        List<String> sentences = new ArrayList<>();
        List<String> tagSeqs = new ArrayList<>();
        while (scanner.hasNext()) {
            Scanner seq = new Scanner(scanner.next());
            seq.useDelimiter("\n");
            StringBuilder sentence = new StringBuilder(), tagSeq = new StringBuilder();
            while (seq.hasNext()) {
                String line = seq.next();
                if (line.split("\\s").length != 2) {
                    _logger.log(Level.SEVERE, "wrong line: " + line);
                }
                sentence.append(line.split("\\s")[0]);
                sentence.append(" ");
                tagSeq.append(line.split("\\s")[1]);
                tagSeq.append(" ");
                _tagset.add(line.split("\\s")[1]);
            }
            sentences.add(sentence.toString());
            tagSeqs.add(tagSeq.toString());
        }
        return TrainPar(sentences.toArray(new String[sentences.size()]),
                tagSeqs.toArray(new String[tagSeqs.size()]), learningRate, maxIter, thresh, numThreads);
    }

    // 迭代融和版本
    final public boolean TrainPar(String[] sentences, String[] tagSeqs, double learningRate, int maxIter,
                               double thresh, int numThreads) throws InterruptedException, IOException {
        _logger.log(Level.INFO, String.format("Training started: %d sentences, %d features, %d threads.",
                sentences.length, numFeatures, numThreads));
        if (_model == null)
            _model = new Feature(sentences.length * 7 * numFeatures);
        Feature[] threadModels = new Feature[numThreads];
        for (int i = 0; i < threadModels.length; i++) {
            threadModels[i] = new Feature(_model);
        }
        Feature aveModel = new Feature(_model);
        double preErr = -thresh;
        int iter;
        for (iter = 1; iter <= maxIter; iter++) {
            long startTime = System.currentTimeMillis();
            final double[] err = {0.0};
            final int[] finishedSentences = {0};
            ReadWriteLock lock = new ReentrantReadWriteLock();
            final Lock writeLock = lock.writeLock();
            ExecutorService pool = Executors.newFixedThreadPool(numThreads);
            final int len = sentences.length / numThreads;
            for (int i = 0; i < numThreads; i++) {
                final int finalI = i;
                final int finalIter = iter;
                final double finalLearningRate = learningRate;
                pool.submit(
                        new Thread () {
                            public void run() {
                                for (int j = finalI * len; j < Math.min(finalI *len+len, sentences.length); j++) {
                                    String tagSeqStar = Tag(sentences[finalI], threadModels[finalI]);
                                    Feature featureStar = CreateGlobalFeature(sentences[finalI], tagSeqStar);
                                    Feature featureTrue = CreateGlobalFeature(sentences[finalI], tagSeqs[finalI]);
                                    featureTrue.MinusAssign(featureStar);
                                    writeLock.lock();
                                    err[0] += featureTrue.absSum();
                                    finishedSentences[0] += 1;
//                                    if ((finishedSentences[0] + 1) % 1000 == 0)
//                                    System.out.println(String.format("Iter %d: %d/%d ", finalIter,
//                                            finishedSentences[0] +1, sentences.length));
                                    writeLock.unlock();
                                    threadModels[finalI].PlusAssign(featureTrue.DotProductAssign(finalLearningRate));
                                }
                            }
                        }
                );
            }
            pool.shutdown();
            while (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
//                System.out.println(String.format("60s report: Iter %d: %d/%d ", iter,
//                        finishedSentences[0] +1, sentences.length));
            }
            for (Feature model : threadModels) {
                _model.PlusAssign(model);
            }
            for (int i = 0; i < threadModels.length; i++) {
                threadModels[i] = new Feature(_model);
            }
            aveModel.PlusAssign(_model);
            long endTime = System.currentTimeMillis();
            System.out.println(String.format("Iter %d: err: %.2f time: %.3fs lrate: %.3f",
                    iter, err[0], (endTime-startTime)/1000.0, learningRate));
            if (Math.abs(preErr - err[0]) < thresh)
                break;
            if (preErr < err[0])
                learningRate *= 0.8;
            preErr = err[0];
        }
        aveModel.DotProductAssign(1.0/(sentences.length*iter));
        _model = aveModel;
        _SaveModel(new File("result/model.txt"));
        return true;
    }

    // 线程池版本
    final public boolean TrainPool(String[] sentences, String[] tagSeqs, double learningRate, int maxIter,
                               double thresh, int numThreads)
            throws IOException, InterruptedException {
        _logger.log(Level.INFO, String.format("Training started: %d sentences, %d features, %d threads.",
                sentences.length, numFeatures, numThreads));
        if (_model == null)
            _model = new Feature(sentences.length * 7 * numFeatures);
        Feature aveModel = new Feature(_model);
        double preErr = -thresh;
        int iter;
        for (iter = 1; iter <= maxIter; iter++) {
            long startTime = System.currentTimeMillis();
            final double[] err = {0.0};
            final int[] finishedThreads = {0};
            ReadWriteLock lock = new ReentrantReadWriteLock();
            final Lock readLock = lock.readLock();
            final Lock writeLock = lock.writeLock();
            ExecutorService pool = Executors.newFixedThreadPool(numThreads);
            for (int i = 0; i < sentences.length; i++) {
                final int finalI = i;
                final int finalIter = iter;
                final double finalLearningRate = learningRate;
                pool.submit(
                        new Thread() {
                            public void run() {
                                readLock.lock();
                                String tagSeqStar = Tag(sentences[finalI]);
                                Feature featureStar = CreateGlobalFeature(sentences[finalI], tagSeqStar);
                                Feature featureTrue = CreateGlobalFeature(sentences[finalI], tagSeqs[finalI]);
                                readLock.unlock();
                                featureTrue.MinusAssign(featureStar);
                                writeLock.lock();
                                err[0] += featureTrue.absSum();
                                _model.PlusAssign(featureTrue.DotProductAssign(finalLearningRate));
//                                if ((finishedThreads[0] + 1) % 1000 == 0)
//                                    System.out.println(String.format("Iter %d: %d/%d ", finalIter,
//                                            finishedThreads[0] +1, sentences.length));
//                                finishedThreads[0] += 1;
                                writeLock.unlock();
                            }
                        }
                );
            }
            pool.shutdown();
            while (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
//                System.out.println(String.format("60s report: Iter %d: %d/%d ", iter,
//                        finishedThreads[0] +1, sentences.length));
            }
            aveModel.PlusAssign(_model);
            long endTime = System.currentTimeMillis();
            System.out.println(String.format("Iter %d: err: %.2f time: %.3fs lrate: %.3f",
                    iter, err[0], (endTime-startTime)/1000.0, learningRate));
            if (Math.abs(preErr - err[0]) < thresh)
                break;
            if (preErr < err[0])
                learningRate *= 0.8;
            preErr = err[0];
        }
        aveModel.DotProductAssign(1.0/(sentences.length*iter));
        _model = aveModel;
        _SaveModel(new File("result/model.txt"));
        return true;
    }

    // 用于调试或性能分析的单线程版本
    final public boolean Train(String[] sentences, String[] tagSeqs, double learningRate, int maxIter, double thresh)
            throws IOException {
        _logger.log(Level.INFO, String.format("Training started: %d sentences.", sentences.length));
        _model = new Feature(sentences.length * 7 * numFeatures);
        double preErr = 100.0;
        for (int iter = 1; iter <= maxIter; iter++) {
            long startTime = System.currentTimeMillis();
            double err = 0.0;
            for (int i = 0; i < sentences.length; i++) {
                String tagSeqStar = Tag(sentences[i], _model);
                Feature featureStar = CreateGlobalFeature(sentences[i], tagSeqStar);
                Feature featureTrue = CreateGlobalFeature(sentences[i], tagSeqs[i]);
                featureTrue.MinusAssign(featureStar);
                err += featureTrue.absSum();
                _model.PlusAssign(featureTrue.DotProductAssign(learningRate));
                if ((i + 1) % 1000 == 0)
                    System.out.println(String.format("Iter %d: %d/%d\r", iter, i+1, sentences.length));
            }
            long endTime = System.currentTimeMillis();
            System.out.println(String.format("Iter %d: err: %.2f time: %.3fs", iter, err, (endTime-startTime)/1000.0));
            if (Math.abs(preErr - err) < thresh)
                break;
            preErr = err;
        }
        _SaveModel(new File("result/model.txt"));
        return true;
    }

    final private double _GetScore(Feature feature, Feature model) {
        double score = 0.0;
        for (HashMap.Entry<String, Double> e : feature.feature.entrySet()) {
            score += e.getValue() * model.getOrDefault(e.getKey(), -2.0);
        }
        return score;
    }

    final private void _SaveModel(File file) throws IOException {
        _logger.log(Level.INFO, String.format("Save model to %s", file.getName()));
        FileOutputStream fos = new FileOutputStream(file);
        fos.write(String.format("#tagset#%d\n", _tagset.size()).getBytes());
        for (String tag : _tagset) {
            fos.write(String.format("%s\n", tag).getBytes());
        }
        fos.write(String.format("#model#%d\n", _model.feature.size()).getBytes());
        for (String k : _model.feature.keySet()) {
            fos.write(String.format("%s\t%f\n", k, _model.get(k)).getBytes());
        }
    }

    final private void _Viterbi(String[] words, String[] tagSeq, Feature model) {
        String[] tagSet = new String[_tagset.size()];
        int ti = 0;
        for (String v : _tagset) {
            tagSet[ti] = v;
            ti++;
        };
        double[][] f = new double[words.length][tagSet.length];
        int[][] backTrace = new int[words.length][tagSet.length];
        for (int i = 0; i < tagSet.length; i++) {
            String[] localTags = new String[1];
            localTags[0] = tagSet[i];
            f[0][i] = _GetScore(CreateLocalFeature(words, localTags, 0), model);
            backTrace[0][i] = 0;
        }
        for (int i = 1; i < words.length; i++) {
            for (int j = 0; j < tagSet.length; j++) {
                double maxScore = -2000000000;
                int maxPrev = -1;
                for (int prev = 0; prev < tagSet.length; prev++) {
                    String[] localTags = new String[words.length];
                    localTags[i-1] = tagSet[prev];
                    localTags[i] =  tagSet[j];
                    double tmpScore = f[i-1][prev] + _GetScore(CreateLocalFeature(words, localTags, i), model);
                    if (tmpScore > maxScore) {
                        maxScore = tmpScore;
                        maxPrev = prev;
                    }
                }
                f[i][j] = maxScore;
                backTrace[i][j] = maxPrev;
            }
        }
        double maxScore = -2000000000;
        int maxJ = 0;
        for (int j = 0; j < tagSet.length; j++) {
            if (f[words.length-1][j] > maxScore) {
                maxScore = f[words.length-1][j];
                maxJ = j;
            }
        }
        for (int i = words.length - 1; i >= 0; i--) {
            tagSeq[i] = tagSet[maxJ];
            maxJ = backTrace[i][maxJ];
        }
    }
}
