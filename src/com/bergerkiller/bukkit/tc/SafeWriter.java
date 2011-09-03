package com.bergerkiller.bukkit.tc;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.util.logging.Level;


public class SafeWriter {
	private String filename;
	private BufferedWriter w;
	
	public SafeWriter(String filename) {
		this.filename = filename;
		try {
			File f = new File(filename);
			if (f.exists()) f.delete();
			f.getParentFile().mkdirs();
			w = new BufferedWriter(new FileWriter(filename));
		} catch (FileNotFoundException ex) {
			Util.log(Level.SEVERE, "Failed to write to: " + filename);
			ex.printStackTrace();
		} catch (Exception ex) {
			Util.log(Level.SEVERE, "Failed to open a write stream to: " + this.filename);
			ex.printStackTrace();
		}
	}
	
	public void writeLine(String line) {
		if (this.w != null) {
			try {
				w.write(line);
				w.newLine();
			} catch (Exception ex) {
				Util.log(Level.SEVERE, "Error while writing data to file: " + this.filename);
				this.close();
				this.w = null;
			}
		}
	}
	
	public void close() {
		if (this.w == null) return;
		try {
			this.w.close();
		} catch (Exception ex) {
			Util.log(Level.SEVERE, "Error while closing stream: " + this.filename);
			ex.printStackTrace();
		}
	}

}
