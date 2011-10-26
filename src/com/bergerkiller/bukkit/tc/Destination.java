package com.bergerkiller.bukkit.tc;

import org.bukkit.block.BlockFace;

public class Destination {
  private BlockFace dir;
  private double dist;
  
  public Destination(BlockFace dir, double dist){
    this.dir = dir;
    this.dist = dist;
  }
  
  public BlockFace getDir(){
    return dir;
  }
  
  public double getDist(){
    return dist;
  }
  public void setDir(BlockFace dir){
    this.dir = dir;
  }
  public void setDist(double dist){
    this.dist = dist;
  }
}