import java.util.*; 

public class Member extends Person {
  private List<Borrowed> borroweds = new ArrayList<>();
  private List<Reservation> reserves = new ArrayList<>();
}
