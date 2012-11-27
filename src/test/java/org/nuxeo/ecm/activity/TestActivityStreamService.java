/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Thomas Roger <troger@nuxeo.com>
 */

package org.nuxeo.ecm.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_REMOVED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.ClientRuntimeException;
import org.nuxeo.ecm.core.persistence.PersistenceProvider;
import org.nuxeo.ecm.core.test.annotations.Granularity;
import org.nuxeo.ecm.core.test.annotations.RepositoryConfig;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

/**
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 * @since 5.5
 */
@RunWith(FeaturesRunner.class)
@Features(ActivityFeature.class)
@RepositoryConfig(cleanup = Granularity.METHOD)
public class TestActivityStreamService {

    @Inject
    protected ActivityStreamService activityStreamService;

    @Before
    public void cleanupDatabase() throws ClientException {
        ((ActivityStreamServiceImpl) activityStreamService).getOrCreatePersistenceProvider().run(
                true, new PersistenceProvider.RunVoid() {
                    @Override
                    public void runWith(EntityManager em) {
                        Query query = em.createQuery("delete from Activity");
                        query.executeUpdate();
                        query = em.createQuery("delete from Tweet");
                        query.executeUpdate();
                    }
                });
        TransactionHelper.commitOrRollbackTransaction();
        TransactionHelper.startTransaction();
    }

    @Test
    public void serviceRegistration() {
        assertNotNull(activityStreamService);
    }

    @Test
    public void shouldStoreAnActivity() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        List<Activity> activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());

        Activity storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());
    }

    @Test
    public void shouldCallRegisteredActivityStreamFilter() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        Map<String, ActivityStreamFilter> filters = ((ActivityStreamServiceImpl) activityStreamService).activityStreamFilters;
        assertEquals(2, filters.size());

        List<Activity> activities = activityStreamService.query(
                DummyActivityStreamFilter.ID, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());

        activities = filters.get(DummyActivityStreamFilter.ID).query(null,
                null, 0, 0);
        assertEquals(1, activities.size());

        Activity storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());
    }

    @Test(expected = ClientRuntimeException.class)
    public void shouldThrowExceptionIfFilterIsNotRegistered() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        activityStreamService.query("nonExistingFilter", null);
    }

    @Test
    public void shouldHandlePagination() {
        addTestActivities(10);

        List<Activity> activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 5);
        assertEquals(5, activities.size());
        for (int i = 0; i < 5; i++) {
            assertEquals("activity" + i, activities.get(i).getObject());
        }

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 5, 5);
        assertEquals(5, activities.size());
        for (int i = 5; i < 10; i++) {
            assertEquals("activity" + i, activities.get(i - 5).getObject());
        }

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 15);
        assertEquals(10, activities.size());
        for (int i = 0; i < 10; i++) {
            assertEquals("activity" + i, activities.get(i).getObject());
        }

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 15, 5);
        assertEquals(0, activities.size());
    }

    protected void addTestActivities(int activitiesCount) {
        for (int i = 0; i < activitiesCount; i++) {
            Activity activity = new ActivityImpl();
            activity.setActor("Administrator");
            activity.setVerb("test");
            activity.setObject("activity" + i);
            activity.setPublishedDate(new Date());
            activityStreamService.addActivity(activity);
        }
    }

    @Test
    public void shouldRemoveActivities() {
        addTestActivities(10);

        List<Activity> allActivities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 0);
        assertEquals(10, allActivities.size());

        Activity firstActivity = allActivities.get(0);
        activityStreamService.removeActivities(Collections.singleton(firstActivity));

        allActivities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 0);
        assertEquals(9, allActivities.size());
        assertFalse(allActivities.contains(firstActivity));

        List<Activity> activities = allActivities.subList(0, 4);
        activityStreamService.removeActivities(activities);
        allActivities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 0);
        assertEquals(5, allActivities.size());

        activities = allActivities.subList(0, 5);
        activityStreamService.removeActivities(activities);
        allActivities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null, 0, 0);
        assertTrue(allActivities.isEmpty());
    }

    @Test
    public void shouldStoreLabelKeyForActivityVerbs() {
        // compat test
        ActivityVerbRegistry activityVerbRegistry = ((ActivityStreamServiceImpl) activityStreamService).activityVerbRegistry;
        assertNotNull(activityVerbRegistry);

        ActivityVerb activityVerb = activityVerbRegistry.get("compatVerb");
        assertNotNull(activityVerb);
        assertEquals("label.activity.compatLabel",
                activityVerb.getLabelKey());
    }

    @Test
    public void shouldStoreActivityVerbs() {
        ActivityVerbRegistry activityVerbRegistry = ((ActivityStreamServiceImpl) activityStreamService).activityVerbRegistry;
        assertNotNull(activityVerbRegistry);

        ActivityVerb activityVerb = activityVerbRegistry.get(DOCUMENT_CREATED);
        assertNotNull(activityVerb);
        assertEquals("label.activity.documentCreated",
                activityVerb.getLabelKey());
        activityVerb = activityVerbRegistry.get(DOCUMENT_UPDATED);
        assertNotNull(activityVerb);
        assertEquals("label.activity.documentUpdated",
                activityVerb.getLabelKey());
        activityVerb = activityVerbRegistry.get(DOCUMENT_REMOVED);
        assertNotNull(activityVerb);
        assertEquals("label.activity.documentRemoved",
                activityVerb.getLabelKey());
    }

    @Test
    public void shouldStoreTweetActivities() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb(TweetActivityStreamFilter.TWEET_VERB);
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put("seenBy", "Bob");
        List<Activity> activities = activityStreamService.query(
                TweetActivityStreamFilter.ID, parameters);
        assertEquals(1, activities.size());
        Activity storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());

        parameters = new HashMap<String, Serializable>();
        parameters.put("seenBy", "Joe");
        activities = activityStreamService.query(TweetActivityStreamFilter.ID,
                parameters);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());

        parameters = new HashMap<String, Serializable>();
        parameters.put("seenBy", "John");
        activities = activityStreamService.query(TweetActivityStreamFilter.ID,
                parameters);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());
    }

    @Test
    public void shouldRemoveTweets() throws ClientException {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb(TweetActivityStreamFilter.TWEET_VERB);
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activity = activityStreamService.addActivity(activity);

        Map<String, Serializable> parameters = new HashMap<String, Serializable>();
        parameters.put("seenBy", "Bob");
        List<Activity> activities = activityStreamService.query(
                TweetActivityStreamFilter.ID, parameters);
        assertEquals(1, activities.size());

        List<TweetActivity> tweets = getAllTweetActivities();
        assertEquals(3, tweets.size());

        activityStreamService.removeActivities(Collections.singleton(activity));
        activities = activityStreamService.query(TweetActivityStreamFilter.ID,
                parameters);
        assertTrue(activities.isEmpty());

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertTrue(activities.isEmpty());

        tweets = getAllTweetActivities();
        assertTrue(tweets.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private List<TweetActivity> getAllTweetActivities() throws ClientException {
        return ((ActivityStreamServiceImpl) activityStreamService).getOrCreatePersistenceProvider().run(
                true,
                new PersistenceProvider.RunCallback<List<TweetActivity>>() {
                    @Override
                    public List<TweetActivity> runWith(EntityManager em) {
                        Query query = em.createQuery("from Tweet");
                        return query.getResultList();
                    }
                });
    }

    @Test
    public void shouldStoreActivityStreams() {
        Map<String, ActivityStream> activityStreams = ((ActivityStreamServiceImpl) activityStreamService).activityStreamRegistry.activityStreams;
        assertNotNull(activityStreams);
        assertEquals(2, activityStreams.size());

        ActivityStream activityStream = activityStreamService.getActivityStream("userActivityStream");
        assertNotNull(activityStream);
        assertEquals("userActivityStream", activityStream.getName());
        List<String> verbs = activityStream.getVerbs();
        assertNotNull(verbs);
        assertEquals(3, verbs.size());
        assertTrue(verbs.contains("documentCreated"));
        assertTrue(verbs.contains("documentModified"));
        assertTrue(verbs.contains("circle"));

        activityStream = activityStreamService.getActivityStream("anotherStream");
        assertNotNull(activityStream);
        assertEquals("anotherStream", activityStream.getName());
        verbs = activityStream.getVerbs();
        assertNotNull(verbs);
        assertEquals(1, verbs.size());
        assertTrue(verbs.contains("documentDeleted"));
    }

    @Test
    public void shouldStoreAnActivityReply() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        List<Activity> activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());

        Activity storedActivity = activities.get(0);
        assertEquals(activity.getActor(), storedActivity.getActor());
        assertEquals(activity.getVerb(), storedActivity.getVerb());
        assertEquals(activity.getObject(), storedActivity.getObject());

        long replyPublishedDate = new Date().getTime();
        ActivityReply reply = new ActivityReply("bender", "Bender",
                "First reply", replyPublishedDate);
        ActivityReply storedReply = activityStreamService.addActivityReply(activity.getId(), reply);
        assertEquals(storedActivity.getId() + "-reply-1", storedReply.getId());

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertNotNull(storedActivity.getReplies());
        List<ActivityReply> replies = storedActivity.getActivityReplies();
        assertFalse(replies.isEmpty());
        assertEquals(1, replies.size());
        storedReply = replies.get(0);
        assertEquals(storedActivity.getId() + "-reply-1", storedReply.getId());
        assertEquals("bender", storedReply.getActor());
        assertEquals("Bender", storedReply.getDisplayActor());
        assertEquals(replyPublishedDate, storedReply.getPublishedDate());
        assertEquals("First reply", storedReply.getMessage());
    }

    @Test
    public void shouldStoreMultipleActivityReplies() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        List<Activity> activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());

        Activity storedActivity = activities.get(0);

        long firstReplyPublishedDate = new Date().getTime();
        ActivityReply firstReply = new ActivityReply("bender", "Bender",
                "First reply", firstReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), firstReply);
        long secondReplyPublishedDate = new Date().getTime();
        ActivityReply secondReply = new ActivityReply("bender", "Bender",
                "Second reply", secondReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), secondReply);
        long thirdReplyPublishedDate = new Date().getTime();
        ActivityReply thirdReply = new ActivityReply("fry", "Fry",
                "Third reply", thirdReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), thirdReply);
        long fourthReplyPublishedDate = new Date().getTime();
        ActivityReply fourthReply = new ActivityReply("leela", "Leela",
                "Fourth reply", fourthReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), fourthReply);

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertNotNull(storedActivity.getReplies());
        List<ActivityReply> replies = storedActivity.getActivityReplies();
        assertFalse(replies.isEmpty());
        assertEquals(4, replies.size());

        ActivityReply storedReply = replies.get(0);
        assertEquals(storedActivity.getId() + "-reply-1", storedReply.getId());
        assertEquals("bender", storedReply.getActor());
        assertEquals("Bender", storedReply.getDisplayActor());
        assertEquals(firstReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("First reply", storedReply.getMessage());
        storedReply = replies.get(1);
        assertEquals(storedActivity.getId() + "-reply-2", storedReply.getId());
        assertEquals("bender", storedReply.getActor());
        assertEquals("Bender", storedReply.getDisplayActor());
        assertEquals(secondReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Second reply", storedReply.getMessage());
        storedReply = replies.get(2);
        assertEquals(storedActivity.getId() + "-reply-3", storedReply.getId());
        assertEquals("fry", storedReply.getActor());
        assertEquals("Fry", storedReply.getDisplayActor());
        assertEquals(thirdReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Third reply", storedReply.getMessage());
        storedReply = replies.get(3);
        assertEquals(storedActivity.getId() + "-reply-4", storedReply.getId());
        assertEquals("leela", storedReply.getActor());
        assertEquals("Leela", storedReply.getDisplayActor());
        assertEquals(fourthReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Fourth reply", storedReply.getMessage());
    }

    @Test
    public void shouldRemoveActivityReply() {
        Activity activity = new ActivityImpl();
        activity.setActor("Administrator");
        activity.setVerb("test");
        activity.setObject("yo");
        activity.setPublishedDate(new Date());
        activityStreamService.addActivity(activity);

        List<Activity> activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());

        Activity storedActivity = activities.get(0);

        long firstReplyPublishedDate = new Date().getTime();
        ActivityReply firstReply = new ActivityReply("bender", "Bender",
                "First reply", firstReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), firstReply);
        long secondReplyPublishedDate = new Date().getTime();
        ActivityReply secondReply = new ActivityReply("bender", "Bender",
                "Second reply", secondReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), secondReply);
        long thirdReplyPublishedDate = new Date().getTime();
        ActivityReply thirdReply = new ActivityReply("fry", "Fry",
                "Third reply", thirdReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), thirdReply);
        long fourthReplyPublishedDate = new Date().getTime();
        ActivityReply fourthReply = new ActivityReply("leela", "Leela",
                "Fourth reply", fourthReplyPublishedDate);
        activityStreamService.addActivityReply(storedActivity.getId(), fourthReply);

        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertNotNull(storedActivity.getReplies());
        List<ActivityReply> replies = storedActivity.getActivityReplies();
        assertFalse(replies.isEmpty());
        assertEquals(4, replies.size());

        ActivityReply storedReply = replies.get(0);
        assertEquals(storedActivity.getId() + "-reply-1", storedReply.getId());
        assertEquals("bender", storedReply.getActor());
        assertEquals("Bender", storedReply.getDisplayActor());
        assertEquals(firstReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("First reply", storedReply.getMessage());
        storedReply = replies.get(1);
        assertEquals(storedActivity.getId() + "-reply-2", storedReply.getId());
        assertEquals("bender", storedReply.getActor());
        assertEquals("Bender", storedReply.getDisplayActor());
        assertEquals(secondReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Second reply", storedReply.getMessage());
        storedReply = replies.get(2);
        assertEquals(storedActivity.getId() + "-reply-3", storedReply.getId());
        assertEquals("fry", storedReply.getActor());
        assertEquals("Fry", storedReply.getDisplayActor());
        assertEquals(thirdReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Third reply", storedReply.getMessage());
        storedReply = replies.get(3);
        assertEquals(storedActivity.getId() + "-reply-4", storedReply.getId());
        assertEquals("leela", storedReply.getActor());
        assertEquals("Leela", storedReply.getDisplayActor());
        assertEquals(fourthReplyPublishedDate, storedReply.getPublishedDate());
        assertEquals("Fourth reply", storedReply.getMessage());

        activityStreamService.removeActivityReply(activity.getId(), thirdReply.getId());
        activities = activityStreamService.query(
                ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(1, activities.size());
        storedActivity = activities.get(0);
        assertNotNull(storedActivity.getReplies());
        replies = storedActivity.getActivityReplies();
        assertFalse(replies.isEmpty());
        assertEquals(3, replies.size());

        for (ActivityReply reply : replies) {
            assertFalse(reply.getMessage().equals("Third reply"));
        }
    }

    @Test
    public void testActivityUpgraders() {
        Activity activity = new ActivityImpl();
        activity.setActor("Bender");
        activity.setVerb("hi");
        activity.setObject("Hello");
        activityStreamService.addActivity(activity);
        activity = new ActivityImpl();
        activity.setActor("Leela");
        activity.setVerb("hi");
        activity.setObject("Hello Fry");
        activityStreamService.addActivity(activity);

        ActivitiesList activities = activityStreamService.query(
                        ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(2, activities.size());

        activity = activities.get(0);
        assertEquals("Bender", activity.getActor());
        activity = activities.get(1);
        assertEquals("Leela", activity.getActor());

        ((ActivityStreamServiceImpl) activityStreamService).upgradeActivities();

        activities = activityStreamService.query(
                        ActivityStreamService.ALL_ACTIVITIES, null);
        assertNotNull(activities);
        assertEquals(2, activities.size());

        activity = activities.get(0);
        assertEquals("Dummy Actor", activity.getActor());
        activity = activities.get(1);
        assertEquals("Dummy Actor", activity.getActor());
    }

    @Test
    public void testActivityUpgradersOrder() {
        List<ActivityUpgrader> upgraders = ((ActivityStreamServiceImpl) activityStreamService).activityUpgraderRegistry.getOrderedActivityUpgraders();
        assertEquals(2, upgraders.size());
        ActivityUpgrader upgrader = upgraders.get(0);
        assertEquals("anotherDummyUpgrader", upgrader.getName());
        assertEquals(5, upgrader.getOrder());
        upgrader = upgraders.get(1);
        assertEquals("dummyUpgrader", upgrader.getName());
        assertEquals(10, upgrader.getOrder());
    }

}