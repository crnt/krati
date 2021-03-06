package krati.core.array.basic;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.log4j.Logger;

import krati.core.array.entry.Entry;
import krati.core.array.entry.EntryFactory;
import krati.core.array.entry.EntryValue;

/**
 * AbstractRecoverableArray
 * 
 * @author jwu
 * 
 * <p>
 * 05/09, 2011 - added abstract method getLogger
 * 
 */
abstract class AbstractRecoverableArray<V extends EntryValue> implements RecoverableArray<V> {
  protected int                  _length;         // Length of this array
  protected File                 _directory;      // array home directory
  protected ArrayFile            _arrayFile;      // underlying array file
  protected EntryFactory<V>      _entryFactory;   // factory for creating Entry
  protected ArrayEntryManager<V> _entryManager;   // manager for entry redo logs
  
    /**
     * @param length
     *            Length of the array.
     * @param entrySize
     *            Maximum number of values per entry.
     * @param maxEntries
     *            Maximum number of entries before applying them.
     * @param directory
     *            Directory to store the array file and redo entries.
     */
    protected AbstractRecoverableArray(int length, int elemSize, int entrySize, int maxEntries, File directory, EntryFactory<V> entryFactory) throws Exception {
        _length = length;
        _directory = directory;
        _entryFactory = entryFactory;
        _entryManager = new ArrayEntryManager<V>(this, maxEntries, entrySize);

        if (!_directory.exists()) {
            _directory.mkdirs();
        }

        File file = new File(_directory, "indexes.dat");
        _arrayFile = openArrayFile(file, length /* initial length */, elemSize);
        _length = _arrayFile.getArrayLength();

        init();

        getLogger().info("length:" + _length +
                        " entrySize:" + entrySize +
                        " maxEntries:" + maxEntries + 
                        " directory:" + directory.getAbsolutePath() +
                        " arrayFile:" + _arrayFile.getName());
    }
    
    /**
     * Loads data from the array file.
     */
    protected void init() throws IOException {
        try {
            long lwmScn = _arrayFile.getLwmScn();
            long hwmScn = _arrayFile.getHwmScn();
            if (hwmScn < lwmScn) {
                throw new IOException(_arrayFile.getAbsolutePath() + " is corrupted: lwmScn=" + lwmScn + " hwmScn=" + hwmScn);
            }

            // Initialize entry manager and process entry files on disk if any.
            _entryManager.init(lwmScn, hwmScn);

            // Load data from the array file on disk.
            loadArrayFileData();
        } catch (IOException e) {
            _entryManager.clear();
            getLogger().error(e.getMessage(), e);
            throw e;
        }
    }

    protected final ArrayFile openArrayFile(File file, int initialLength, int elementSize) throws IOException {
        boolean isNew = true;
        if (file.exists()) {
            isNew = false;
        }
        
        ArrayFile arrayFile = new ArrayFile(file, initialLength, elementSize);
        if (isNew) {
            initArrayFile();
        }
        
        return arrayFile;
    }

    protected void initArrayFile() throws IOException {
        // Subclasses need to initialize ArrayFile
    }

    protected abstract void loadArrayFileData() throws IOException;

    protected abstract Logger getLogger();

    public File getDirectory() {
        return _directory;
    }

    public EntryFactory<V> getEntryFactory() {
        return _entryFactory;
    }

    public ArrayEntryManager<V> getEntryManager() {
        return _entryManager;
    }

    @Override
    public boolean hasIndex(int index) {
        return (0 <= index && index < _length);
    }

    @Override
    public final int length() {
        return _length;
    }

    /**
     * Sync array file with all entry logs. The writer will be blocked until all
     * entry logs are applied.
     */
    @Override
    public void sync() throws IOException {
        _entryManager.sync();
        getLogger().info("array saved: length=" + length());
    }

    /**
     * Persists this array.
     */
    @Override
    public void persist() throws IOException {
        _entryManager.persist();
        getLogger().info("array persisted: length=" + length());
    }

    @Override
    public final long getHWMark() {
        return _entryManager.getHWMark();
    }

    @Override
    public final long getLWMark() {
        return _entryManager.getLWMark();
    }

    @Override
    public void updateArrayFile(List<Entry<V>> entryList) throws IOException {
        if(_arrayFile != null) {
            _arrayFile.update(entryList);
        }
    }
}
