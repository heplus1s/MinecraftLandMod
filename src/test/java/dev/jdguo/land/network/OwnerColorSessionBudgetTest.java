package dev.jdguo.land.network;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OwnerColorSessionBudgetTest {
 @Test void repeatedNonceChangesAreBoundedAndExpireWithRollingTime() {
  var budget=new OwnerColorSessionBudget();Object connection=new Object();
  for(int i=0;i<10;i++)assertTrue(budget.allow(connection,i));
  assertFalse(budget.allow(connection,10));assertFalse(budget.allow(connection,9_999_999_999L));
  assertTrue(budget.allow(connection,10_000_000_000L));assertFalse(budget.allow(connection,10_000_000_000L));
  assertTrue(budget.allow(connection,10_000_000_001L));
 }
 @Test void newConnectionGetsFreshBudgetAndNullCannotUseOne() {
  var budget=new OwnerColorSessionBudget();Object connection=new Object();
  assertFalse(budget.allow(null,0));for(int i=0;i<10;i++)assertTrue(budget.allow(connection,0));
  assertFalse(budget.allow(connection,0));assertTrue(budget.allow(new Object(),0));
 }
}
