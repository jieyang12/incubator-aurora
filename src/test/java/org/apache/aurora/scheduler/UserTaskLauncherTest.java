/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.aurora.scheduler;

import com.google.common.base.Optional;
import com.twitter.common.collections.Pair;
import com.twitter.common.testing.easymock.EasyMockTest;

import org.apache.aurora.gen.HostAttributes;
import org.apache.aurora.gen.ScheduleStatus;
import org.apache.aurora.scheduler.async.OfferManager;
import org.apache.aurora.scheduler.mesos.Offers;
import org.apache.aurora.scheduler.state.StateManager;
import org.apache.aurora.scheduler.storage.Storage.StorageException;
import org.apache.aurora.scheduler.storage.entities.IHostAttributes;
import org.apache.aurora.scheduler.storage.testing.StorageTestUtil;
import org.apache.mesos.Protos.OfferID;
import org.apache.mesos.Protos.TaskID;
import org.apache.mesos.Protos.TaskState;
import org.apache.mesos.Protos.TaskStatus;
import org.junit.Before;
import org.junit.Test;

import static org.apache.aurora.gen.ScheduleStatus.FAILED;
import static org.apache.aurora.gen.ScheduleStatus.RUNNING;
import static org.easymock.EasyMock.expect;
import static org.junit.Assert.assertTrue;

public class UserTaskLauncherTest extends EasyMockTest {

  private static final String TASK_ID_A = "task_id_a";

  private static final OfferID OFFER_ID = OfferID.newBuilder().setValue("OfferId").build();
  private static final HostOffer OFFER = new HostOffer(
      Offers.createOffer(4, 1024, 1024, Pair.of(80, 80)),
      IHostAttributes.build(new HostAttributes()));

  private OfferManager offerManager;
  private StateManager stateManager;
  private StorageTestUtil storageUtil;

  private TaskLauncher launcher;

  @Before
  public void setUp() {
    offerManager = createMock(OfferManager.class);
    stateManager = createMock(StateManager.class);
    storageUtil = new StorageTestUtil(this);
    storageUtil.expectOperations();
    launcher = new UserTaskLauncher(storageUtil.storage, offerManager, stateManager);
  }

  @Test
  public void testForwardsOffers() throws Exception {
    offerManager.addOffer(OFFER);

    control.replay();

    assertTrue(launcher.willUse(OFFER));
  }

  @Test
  public void testForwardsStatusUpdates() throws Exception {
    expect(stateManager.changeState(
        storageUtil.mutableStoreProvider,
        TASK_ID_A,
        Optional.<ScheduleStatus>absent(),
        RUNNING,
        Optional.of("fake message")))
        .andReturn(true);

    control.replay();

    TaskStatus status = TaskStatus.newBuilder()
        .setState(TaskState.TASK_RUNNING)
        .setTaskId(TaskID.newBuilder().setValue(TASK_ID_A))
        .setMessage("fake message")
        .build();
    assertTrue(launcher.statusUpdate(status));
  }

  @Test
  public void testForwardsRescindedOffers() throws Exception {
    launcher.cancelOffer(OFFER_ID);

    control.replay();

    launcher.cancelOffer(OFFER_ID);
  }

  @Test(expected = StorageException.class)
  public void testFailedStatusUpdate() throws Exception {
    expect(stateManager.changeState(
        storageUtil.mutableStoreProvider,
        TASK_ID_A,
        Optional.<ScheduleStatus>absent(),
        RUNNING,
        Optional.of("fake message")))
        .andThrow(new StorageException("Injected error"));

    control.replay();

    TaskStatus status = TaskStatus.newBuilder()
        .setState(TaskState.TASK_RUNNING)
        .setTaskId(TaskID.newBuilder().setValue(TASK_ID_A))
        .setMessage("fake message")
        .build();
    launcher.statusUpdate(status);
  }

  @Test
  public void testMemoryLimitTranslationHack() throws Exception {
    expect(stateManager.changeState(
        storageUtil.mutableStoreProvider,
        TASK_ID_A,
        Optional.<ScheduleStatus>absent(),
        FAILED,
        Optional.of(UserTaskLauncher.MEMORY_LIMIT_DISPLAY)))
        .andReturn(false);

    control.replay();

    TaskStatus status = TaskStatus.newBuilder()
        .setState(TaskState.TASK_FAILED)
        .setTaskId(TaskID.newBuilder().setValue(TASK_ID_A))
        .setMessage("Memory limit exceeded: Requested 256MB, Used 256MB.\n\n"
            + "MEMORY STATISTICS: \n"
            + "cache 20422656\n"
            + "rss 248012800\n"
            + "mapped_file 8192\n"
            + "pgpgin 28892\n"
            + "pgpgout 6791\n"
            + "inactive_anon 90112\n"
            + "active_anon 245768192\n"
            + "inactive_file 19685376\n"
            + "active_file 700416\n"
            + "unevictable 0\n"
            + "hierarchical_memory_limit 268435456\n"
            + "total_cache 20422656\n"
            + "total_rss 248012800\n"
            + "total_mapped_file 8192\n"
            + "total_pgpgin 28892\n"
            + "total_pgpgout 6791\n"
            + "total_inactive_anon 90112\n"
            + "total_active_anon 245768192\n"
            + "total_inactive_file 19685376\n"
            + "total_active_file 700416\n"
            + "total_unevictable 0 ")
        .build();
    launcher.statusUpdate(status);
  }
}
