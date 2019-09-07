public class Identification {
	//private int myKey;
	private String ToplevelIdent;
	private String SecondaryIdent;

	/*public void setKey (int key) {
		this.myKey = key;
	}*/
	public void setToplevelIdent (String ToplevelIdent) {
		this.ToplevelIdent = ToplevelIdent;
	}
	public void setSecondaryIdent (String SecondaryIdent) {
		this.SecondaryIdent = SecondaryIdent;
	}
	/*public int getKey() {
		return this.myKey;
	}*/
	public String getToplevelIdent() {
		return this.ToplevelIdent;
	}
	public String getSecondaryIdent() {
		return this.SecondaryIdent;
	}
	public Identification(String ToplevelIdent,String SecondaryIdent){
		//myKey = key;
		ToplevelIdent = this.ToplevelIdent;
		SecondaryIdent = this.SecondaryIdent;
	}
}

