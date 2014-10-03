package org.sample.java.helloworld;

import java.io.Serializable;

public class MyQuery implements Serializable {
  String day;

  public MyQuery(String day) {
    this.day = day;
  }
}
