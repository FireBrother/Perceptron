package com.company;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException, InterruptedException {
        Perceptron p = new Perceptron();
        p.Train(new File("data/trn.txt"), 1, 100, 1000, 24);
        p.LoadModel(new File("result/model.txt"));
//        File file = new File("data/dev.wrd");
//        File ofile = new File("result/dev.rst");
//        Scanner scanner = new Scanner(new FileInputStream(file));
//        scanner.useDelimiter("\n\n");
//        FileOutputStream os = new FileOutputStream(ofile);
//        while (scanner.hasNext()) {
//            String line = scanner.next();
//            line = line.replace('\n', ' ');
//            String[] tags = p.Tag(line.split(" "));
//            for (String tag : tags) {
//                os.write((tag+"\n").getBytes());
//            }
//            os.write("\n".getBytes());
//        }
        System.out.println(p.TagTokenizedString("这 是 一个 比较 长 的 , 包含 词性 比较 多 的 测试 字符 串 吗 ?"));
    }
}
