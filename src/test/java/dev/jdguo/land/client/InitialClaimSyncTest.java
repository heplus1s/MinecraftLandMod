package dev.jdguo.land.client;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class InitialClaimSyncTest {
 @Test void delayedRegistrationEventuallyRequestsAndStopsAfterResponse() {
  var state = new InitialClaimSync();
  for(int n=0;n<60;n++) assertFalse(state.tick(true,false));
  for(int n=0;n<19;n++) assertFalse(state.tick(true,true));
  assertTrue(state.tick(true,true));
  state.received();
  for(int n=0;n<200;n++) assertFalse(state.tick(true,true));
 }
 @Test void noPacketsWhileDisconnectedOrVanillaAndReconnectResets() {
  var state = new InitialClaimSync();
  for(int n=0;n<200;n++) assertFalse(state.tick(false,false));
  state.received();state.reset();
  for(int n=0;n<19;n++) assertFalse(state.tick(true,true));
  assertTrue(state.tick(true,true));
 }
 @Test void joinSnapshotDoesNotSuppressExplicitSalesResync() {
  var state = new InitialClaimSync(); state.received();
  for(int n=0;n<19;n++) assertFalse(state.tick(true,true));
  assertTrue(state.tick(true,true));
  state.received();
  for(int n=0;n<100;n++) assertFalse(state.tick(true,true));
 }
 @Test void missingResponseRetriesAtMostOnceEachSecond() {
  var state = new InitialClaimSync();int requests=0;
  for(int n=0;n<100;n++) if(state.tick(true,true)) requests++;
  assertEquals(5,requests);
 }
}
