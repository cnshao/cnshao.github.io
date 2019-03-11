package cn.hejie.test;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.Lock;

public class Test {
        public static void main(String[] args){
            SoftReference<String> s =new SoftReference<String>(new String("123"));

            WeakReference<String> sr = new WeakReference<String>(new String("hello"));

            System.out.println(sr.get());
            System.out.println(s.get());
            Object object = new Object();
            Object[] objArr = new Object[1000000];
            System.out.println(objArr);
            System.gc();                //通知JVM的gc进行垃圾回收
            System.out.println(s.get());
            System.out.println(sr.get());

        }
}
