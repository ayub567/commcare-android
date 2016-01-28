package org.commcare.android.adapters;

import android.app.Activity;
import android.util.Pair;

import net.sqlcipher.database.SQLiteDatabase;

import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.SessionUnavailableException;
import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.application.CommCareApplication;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class EntitySearcher {

    private final String filterRaw;
    private final String[] searchTerms;
    final List<Entity<TreeReference>> matchList;
    //Ugh, annoying.
    final ArrayList<Pair<Integer, Integer>> matchScores;
    private boolean cancelled = false;
    private final boolean mAsyncMode;
    private final boolean mFuzzySearchEnabled;
    private final NodeEntityFactory mNodeFactory;
    private final List<Entity<TreeReference>> full;
    private final Activity context;
    private final EntityListAdapter adapter;
    Thread thread;


    public EntitySearcher(EntityListAdapter adapter,
                          String filterRaw, String[] searchTerms,
                          boolean asyncMode, boolean fuzzySearch,
                          NodeEntityFactory nodeFactory,
                          List<Entity<TreeReference>> full,
                          Activity context) {
        this.adapter = adapter;
        this.full = full;
        this.context = context;
        this.mAsyncMode = asyncMode;
        this.mFuzzySearchEnabled = fuzzySearch;
        this.mNodeFactory = nodeFactory;
        this.filterRaw = filterRaw;
        this.searchTerms = searchTerms;
        matchList = new ArrayList<>();
        matchScores = new ArrayList<>();
    }

    public void startThread() {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //Make sure that we have loaded the necessary cached data
                //before we attempt to search over it
                while (!mNodeFactory.isEntitySetReady()) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                search();
            }
        });
        thread.start();
    }

    public void finish() {
        this.cancelled = true;
        try {
            thread.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void search() {
        Locale currentLocale = Locale.getDefault();

        long startTime = System.currentTimeMillis();
        //It's a bit sketchy here, because this DB lock will prevent
        //anything else from processing
        SQLiteDatabase db;
        try {
            db = CommCareApplication._().getUserDbHandle();
        } catch (SessionUnavailableException e) {
            this.finish();
            return;
        }
        db.beginTransaction();
        for (int index = 0; index < full.size(); ++index) {
            //Every once and a while we should make sure we're not blocking anything with the database
            if (index % 500 == 0) {
                db.yieldIfContendedSafely();
            }
            Entity<TreeReference> e = full.get(index);
            if (cancelled) {
                break;
            }
            if ("".equals(filterRaw)) {
                matchList.add(e);
                continue;
            }

            boolean add = false;
            int score = 0;
            filter:
            for (String filter : searchTerms) {
                add = false;
                for (int i = 0; i < e.getNumFields(); ++i) {
                    String field = e.getNormalizedField(i);
                    if (!"".equals(field) && field.toLowerCase(currentLocale).contains(filter)) {
                        add = true;
                        continue filter;
                    } else {
                        // We possibly now want to test for edit distance for
                        // fuzzy matching
                        if (mFuzzySearchEnabled) {
                            for (String fieldChunk : e.getSortFieldPieces(i)) {
                                Pair<Boolean, Integer> match = StringUtils.fuzzyMatch(filter, fieldChunk);
                                if (match.first) {
                                    add = true;
                                    score += match.second;
                                    continue filter;
                                }
                            }
                        }
                    }
                }
            }
            if (add) {
                //matchList.add(e);
                matchScores.add(Pair.create(index, score));
            }
        }
        if (mAsyncMode) {
            Collections.sort(matchScores, new Comparator<Pair<Integer, Integer>>() {
                @Override
                public int compare(Pair<Integer, Integer> lhs, Pair<Integer, Integer> rhs) {
                    return lhs.second - rhs.second;
                }
            });
        }

        for (Pair<Integer, Integer> match : matchScores) {
            matchList.add(full.get(match.first));
        }

        db.setTransactionSuccessful();
        db.endTransaction();
        if (cancelled) {
            return;
        }

        long time = System.currentTimeMillis() - startTime;
        if (time > 1000) {
            Logger.log("cache", "Presumably finished caching new entities, time taken: " + time + "ms");
        }

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                adapter.setCurrent(matchList);
                adapter.setCurrentSearchTerms(searchTerms);
                adapter.update();
            }
        });
    }
}
