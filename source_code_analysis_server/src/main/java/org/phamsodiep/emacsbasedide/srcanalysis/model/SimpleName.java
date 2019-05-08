package org.phamsodiep.emacsbasedide.srcanalysis.model;


public class SimpleName {
  private Integer start;
  private Integer end;
  private String key;
  private Integer defLine;
  private Integer defCol;
  private String defClassName;


  public Integer getStart() {
    return this.start;
  }

  public Integer getEnd() {
    return this.end;
  }

  public String getKey() {
    return this.key;
  }

  public Integer getDefLine() {
    return this.defLine;
  }

  public Integer getDefCol() {
    return this.defCol;
  }

  public String getDefClassName() {
    return this.defClassName;
  }

  public void setStart(Integer start) {
    this.start = start;
  }

  public void setEnd(Integer end) {
    this.end = end;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public void setDefLine(Integer defLine) {
    this.defLine = defLine;
  }

  public void setDefCol(Integer defCol) {
    this.defCol = defCol;
  }

  public void setDefClassName(String defClassName) {
    this.defClassName = defClassName;
  }
}


