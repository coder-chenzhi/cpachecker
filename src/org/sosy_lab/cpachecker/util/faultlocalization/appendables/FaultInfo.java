package org.sosy_lab.cpachecker.util.faultlocalization.appendables;

import java.util.Objects;
import org.sosy_lab.cpachecker.util.faultlocalization.Fault;
import org.sosy_lab.cpachecker.util.faultlocalization.ranking.NoContextExplanation;

public abstract class FaultInfo implements Comparable<FaultInfo>{

  public enum InfoType{
    FIX(0), HINT(1), REASON(2), RANK_INFO(3);

    private final int reportRank;
    InfoType(int pReportRank) {
      reportRank = pReportRank;
    }
  }

  protected double score;
  protected String description;
  private InfoType type;

  public FaultInfo(InfoType pType){
    type = pType;
  }

  /**
   * Returns a possible fix for pSet. It may be a guess.
   * The set has to have size 1 because NoContextExplanation is designed to explain singletons only.
   * @param pSet the singleton set to calculate the explanation for
   * @return Explanation for pSet
   */
  public static FaultInfo possibleFixFor(Fault pSet){
    return new PotentialFix(InfoType.FIX, new NoContextExplanation().explanationFor(pSet));
  }

  public static FaultInfo fix(String pDescription){
    return new PotentialFix(InfoType.FIX, pDescription);
  }

  public static FaultInfo rankInfo(String pDescription, double pLikelihood){
    return new RankInfo(InfoType.RANK_INFO, pDescription, pLikelihood);
  }

  public static FaultInfo justify(String pDescription){
    return new FaultReason(InfoType.REASON, pDescription);
  }

  public static FaultInfo hint(String pDescription){
    return new Hint(InfoType.HINT, pDescription);
  }

  public double getScore(){
    return score;
  }

  public String getDescription() {
    return description;
  }

  public InfoType getType() {
    return type;
  }

  /**
   * Sort by InfoType then by score.
   * @param info FaultInfo for comparison
   * @return Is this object smaller equal or greater than info
   */
  @Override
  public int compareTo(FaultInfo info){
    if(type.equals(info.type)){
      return Double.compare(info.score, score);
    } else {
      return type.reportRank - info.type.reportRank;
    }
  }

  @Override
  public int hashCode(){
    return Objects.hash(31, description, score, type);
  }

  @Override
  public boolean equals(Object q){
    if(q instanceof FaultInfo){
      FaultInfo r = (FaultInfo)q;
      if(type.equals(r.type)){
        return r.description.equals(description) && score
            == r.score;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    String percent = ((int) (score * 10000)) / 100d + "%";
    return type + ": " + description + " (" + percent + ")";
  }
}
