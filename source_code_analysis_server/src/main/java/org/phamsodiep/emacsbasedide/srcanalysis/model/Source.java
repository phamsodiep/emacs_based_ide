package org.phamsodiep.emacsbasedide.srcanalysis.model;


import java.util.List;


public final class Source {
  private String _id;
  private List<SimpleName> simpleNames;


  public String getId() {
    return this._id;
  }

  public void setId(String id) {
    this._id = id;
  }

  public void setSimpleNames(List<SimpleName> s) {
    this.simpleNames = s;
  }

  public List<SimpleName> getSimpleNames() {
    return this.simpleNames;
  }
}


