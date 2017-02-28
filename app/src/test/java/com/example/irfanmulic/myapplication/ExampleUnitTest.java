package com.example.irfanmulic.myapplication;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;

import static com.example.irfanmulic.myapplication.MainActivity.getBearing;
import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    private Person p1;
    private Person p2;

    @Before
    public void setup(){
        p1 = new Person("p1",32.65702666004866d, -116.9703197479248d,"home");
        p2 = new Person("p2",32.66153390161108d, -116.9703197479248d, "mall");
    }

    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void checkAngle() throws Exception{
        Double bearing = getBearing(p1.lat,p1.lon,p2.lat,p2.lon);
        System.out.println("bearing :" + bearing.toString());
        assertTrue("Should be 0.0", 0.0d-bearing<0.00001);
    }
}