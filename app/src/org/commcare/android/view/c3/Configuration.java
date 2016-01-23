package org.commcare.android.view.c3;

import org.commcare.android.util.InvalidStateException;
import org.commcare.suite.model.graph.GraphData;
import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.util.OrderedHashtable;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Base class for helper classes that build C3 graph configuration.
 * This class itself is not meant to be instantiated. For subclasses,
 * the bulk of the work is done in the constructor. The instantiator
 * can then call getConfiguration and getVariables to get at the JSON
 * configuration and any JavaScript variables that configuration depends on.
 *
 * Created by jschweers on 11/16/2015.
 */
public class Configuration {
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    final GraphData mData;
    final JSONObject mConfiguration;
    final OrderedHashtable<String, String> mVariables;

    Configuration(GraphData data) {
        mData = data;
        mConfiguration = new JSONObject();
        mVariables = new OrderedHashtable<>();
    }

    public JSONObject getConfiguration() {
        return mConfiguration;
    }

    public OrderedHashtable<String, String> getVariables() {
        return mVariables;
    }

    /**
     * Parse given time value into string acceptable to C3.
     *
     * @param value       The value, which may be a YYYY-MM-DD string, a YYYY-MM-DD HH:MM:SS,
     *                    or a double representing days since the epoch.
     * @param description Something to identify the kind of value, used to augment any error message.
     * @return String of format YYYY-MM-DD HH:MM:SS, which is what C3 expects.
     * This expected format is set in DataConfiguration as xFormat.
     * @throws InvalidStateException
     */
    String parseTime(String value, String description) throws InvalidStateException {
        if (value.matches(".*[^0-9.].*")) {
            if (!value.matches(".*:.*")) {
                value += " 00:00:00";
            }
        } else {
            double daysSinceEpoch = parseDouble(value, description);
            Date d = new Date((long)(daysSinceEpoch * DateUtils.DAY_IN_MS));
            value = mDateFormat.format(d);
        }
        return value;
    }

    /**
     * Attempt to parse a double, but fail on NumberFormatException.
     *
     * @param description Something to identify the kind of value, used to augment any error message.
     */
    double parseDouble(String value, String description) throws InvalidStateException {
        try {
            Double numeric = Double.valueOf(value);
            if (numeric.isNaN()) {
                throw new InvalidStateException("Could not understand '" + value + "' in " + description);
            }
            return numeric;
        } catch (NumberFormatException nfe) {
            throw new InvalidStateException("Could not understand '" + value + "' in " + description);
        }
    }
}
