package com.company;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        Perceptron p = new Perceptron();
        p.Train(new File("data/trn.txt"), 0.5, 50, 0.0001);
        p.LoadModel(new File("result/model.txt"));
        System.out.println(p.TagTokenizedString("一致 的 词语 会 有 比较 好 的 效果"));
    }
}
