package marceline.storm.trident.testing;

import org.apache.storm.task.IMetricsContext;
import org.apache.storm.trident.state.ITupleCollection;
import org.apache.storm.tuple.Values;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.storm.trident.state.OpaqueValue;
import org.apache.storm.trident.state.State;
import org.apache.storm.trident.state.StateFactory;
import org.apache.storm.trident.state.ValueUpdater;
import org.apache.storm.trident.state.map.*;
import org.apache.storm.trident.state.snapshot.Snapshottable;

public class MemoryMapStateInitable<T> implements Snapshottable<T>, ITupleCollection, MapState<T> {

    MemoryMapStateBacking<OpaqueValue> _backing;
    SnapshottableMap<T> _delegate;

    public MemoryMapStateInitable(String id, HashMap initState) {
        _backing = new MemoryMapStateBacking(id);
        _delegate = new SnapshottableMap(OpaqueMap.build(_backing), new Values("$MEMORY-MAP-STATE-GLOBAL$"));
	List<List<Object>> compoundKeys = new ArrayList<List<Object>>();
	List<T> values = new ArrayList<T>();
	// key is compound
	for (Object key : initState.keySet()) {
	    List<Object> compoundKey = new ArrayList<Object>();
	    for (Object keyComponent : (List) key){
		compoundKey.add(keyComponent);
	    }
	    compoundKeys.add(compoundKey);
	    values.add((T) (initState.get(key)));
	}
	this.multiPut(compoundKeys, values);
    }

    public T update(ValueUpdater updater) {
        return _delegate.update(updater);
    }

    public void set(T o) {
        _delegate.set(o);
    }

    public T get() {
        return _delegate.get();
    }

    public void beginCommit(Long txid) {
        _delegate.beginCommit(txid);
    }

    public void commit(Long txid) {
        _delegate.commit(txid);
    }

    public Iterator<List<Object>> getTuples() {
        return _backing.getTuples();
    }

    public List<T> multiUpdate(List<List<Object>> keys, List<ValueUpdater> updaters) {
        return _delegate.multiUpdate(keys, updaters);
    }

    public void multiPut(List<List<Object>> keys, List<T> vals) {
        _delegate.multiPut(keys, vals);
    }

    public List<T> multiGet(List<List<Object>> keys) {
        return _delegate.multiGet(keys);
    }

    public static class Factory implements StateFactory {
	HashMap _initState;
        String _id;

        public Factory(Map initState) {
            _id = UUID.randomUUID().toString();
	    _initState = new HashMap(initState);
        }

        @Override
        public State makeState(Map conf, IMetricsContext metrics, int partitionIndex, int numPartitions) {
            return new MemoryMapStateInitable(_id, _initState);
        }
    }

    static ConcurrentHashMap<String, Map<List<Object>, Object>> _dbs = new ConcurrentHashMap<String, Map<List<Object>, Object>>();
    static class MemoryMapStateBacking<T> implements IBackingMap<T>, ITupleCollection {

        public static void clearAll() {
            _dbs.clear();
        }
        Map<List<Object>, T> db;
        Long currTx;

        public MemoryMapStateBacking(String id) {
            if (!_dbs.containsKey(id)) {
                _dbs.put(id, new HashMap());
            }
            this.db = (Map<List<Object>, T>) _dbs.get(id);
        }

        @Override
        public List<T> multiGet(List<List<Object>> keys) {
            List<T> ret = new ArrayList();
            for (List<Object> key : keys) {
                ret.add(db.get(key));
            }
            return ret;
        }

        @Override
        public void multiPut(List<List<Object>> keys, List<T> vals) {
            for (int i = 0; i < keys.size(); i++) {
                List<Object> key = keys.get(i);
                T val = vals.get(i);
                db.put(key, val);
            }
        }

        @Override
        public Iterator<List<Object>> getTuples() {
            return new Iterator<List<Object>>() {

                private Iterator<Map.Entry<List<Object>, T>> it = db.entrySet().iterator();

                public boolean hasNext() {
                    return it.hasNext();
                }

                public List<Object> next() {
                    Map.Entry<List<Object>, T> e = it.next();
                    List<Object> ret = new ArrayList<Object>();
                    ret.addAll(e.getKey());
                    ret.add(((OpaqueValue)e.getValue()).getCurr());
                    return ret;
                }

                public void remove() {
                    throw new UnsupportedOperationException("Not supported yet.");
                }
            };
        }
    }
}
