/*
 * @(#) bt.java   98/03/24
 * Copyright (c) 1998 UW.  All Rights Reserved.
 *         Author: Xiaohu Li (xioahu@cs.wisc.edu).
 *
 */

package btree;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import bufmgr.HashEntryNotFoundException;
import bufmgr.InvalidFrameNumberException;
import bufmgr.PageUnpinnedException;
import bufmgr.ReplacerException;
import diskmgr.Page;
import global.AttrType;
import global.GlobalConst;
import global.PageId;
import global.RID;
import global.SystemDefs;

/**
 * btfile.java This is the main definition of class BTreeFile, which derives
 * from abstract base class IndexFile. It provides an insert/delete interface.
 */
public class BTreeFile extends IndexFile implements GlobalConst {

	private final static int MAGIC0 = 1989;

	private final static String lineSep = System.getProperty("line.separator");

	private static final int PAGE_SIZE = 1004;

	private static final float PERCENTAGE = 50;

	private static final Object BTLeafPage = null;

	private static FileOutputStream fos;
	private static DataOutputStream trace;

	/**
	 * It causes a structured trace to be written to a file. This output is used
	 * to drive a visualization tool that shows the inner workings of the b-tree
	 * during its operations.
	 * 
	 * @param filename
	 *            input parameter. The trace file name
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void traceFilename(String filename) throws IOException {

		fos = new FileOutputStream(filename);
		trace = new DataOutputStream(fos);
	}

	/**
	 * Stop tracing. And close trace file.
	 * 
	 * @exception IOException
	 *                error from the lower layer
	 */
	public static void destroyTrace() throws IOException {
		if (trace != null)
			trace.close();
		if (fos != null)
			fos.close();
		fos = null;
		trace = null;
	}

	private BTreeHeaderPage headerPage;
	private PageId headerPageId;
	private String dbname;

	/**
	 * Access method to data member.
	 * 
	 * @return Return a BTreeHeaderPage object that is the header page of this
	 *         btree file.
	 */
	public BTreeHeaderPage getHeaderPage() {
		return headerPage;
	}

	private PageId get_file_entry(String filename) throws GetFileEntryException {
		try {
			return SystemDefs.JavabaseDB.get_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new GetFileEntryException(e, "");
		}
	}

	private Page pinPage(PageId pageno) throws PinPageException {
		try {
			Page page = new Page();
			SystemDefs.JavabaseBM.pinPage(pageno, page, false/* Rdisk */);
			return page;
		} catch (Exception e) {
			e.printStackTrace();
			throw new PinPageException(e, "");
		}
	}

	private void add_file_entry(String fileName, PageId pageno) throws AddFileEntryException {
		try {
			SystemDefs.JavabaseDB.add_file_entry(fileName, pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new AddFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, false /* = not DIRTY */);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	private void freePage(PageId pageno) throws FreePageException {
		try {
			SystemDefs.JavabaseBM.freePage(pageno);
		} catch (Exception e) {
			e.printStackTrace();
			throw new FreePageException(e, "");
		}

	}

	private void delete_file_entry(String filename) throws DeleteFileEntryException {
		try {
			SystemDefs.JavabaseDB.delete_file_entry(filename);
		} catch (Exception e) {
			e.printStackTrace();
			throw new DeleteFileEntryException(e, "");
		}
	}

	private void unpinPage(PageId pageno, boolean dirty) throws UnpinPageException {
		try {
			SystemDefs.JavabaseBM.unpinPage(pageno, dirty);
		} catch (Exception e) {
			e.printStackTrace();
			throw new UnpinPageException(e, "");
		}
	}

	/**
	 * BTreeFile class an index file with given filename should already exist;
	 * this opens it.
	 * 
	 * @param filename
	 *            the B+ tree file name. Input parameter.
	 * @exception GetFileEntryException
	 *                can not ger the file from DB
	 * @exception PinPageException
	 *                failed when pin a page
	 * @exception ConstructPageException
	 *                BT page constructor failed
	 */
	public BTreeFile(String filename) throws GetFileEntryException, PinPageException, ConstructPageException {

		headerPageId = get_file_entry(filename);

		headerPage = new BTreeHeaderPage(headerPageId);
		dbname = new String(filename);
		/*
		 *
		 * - headerPageId is the PageId of this BTreeFile's header page; -
		 * headerPage, headerPageId valid and pinned - dbname contains a copy of
		 * the name of the database
		 */
	}

	/**
	 * if index file exists, open it; else create it.
	 * 
	 * @param filename
	 *            file name. Input parameter.
	 * @param keytype
	 *            the type of key. Input parameter.
	 * @param keysize
	 *            the maximum size of a key. Input parameter.
	 * @param delete_fashion
	 *            full delete or naive delete. Input parameter. It is either
	 *            DeleteFashion.NAIVE_DELETE or DeleteFashion.FULL_DELETE.
	 * @exception GetFileEntryException
	 *                can not get file
	 * @exception ConstructPageException
	 *                page constructor failed
	 * @exception IOException
	 *                error from lower layer
	 * @exception AddFileEntryException
	 *                can not add file into DB
	 */
	public BTreeFile(String filename, int keytype, int keysize, int delete_fashion)
			throws GetFileEntryException, ConstructPageException, IOException, AddFileEntryException {

		headerPageId = get_file_entry(filename);
		if (headerPageId == null) // file not exist
		{
			headerPage = new BTreeHeaderPage();
			headerPageId = headerPage.getPageId();
			add_file_entry(filename, headerPageId);
			headerPage.set_magic0(MAGIC0);
			headerPage.set_rootId(new PageId(INVALID_PAGE));
			headerPage.set_keyType((short) keytype);
			headerPage.set_maxKeySize(keysize);
			headerPage.set_deleteFashion(delete_fashion);
			headerPage.setType(NodeType.BTHEAD);
		} else {
			headerPage = new BTreeHeaderPage(headerPageId);
		}

		dbname = new String(filename);

	}

	/**
	 * Close the B+ tree file. Unpin header page.
	 * 
	 * @exception PageUnpinnedException
	 *                error from the lower layer
	 * @exception InvalidFrameNumberException
	 *                error from the lower layer
	 * @exception HashEntryNotFoundException
	 *                error from the lower layer
	 * @exception ReplacerException
	 *                error from the lower layer
	 */
	public void close()
			throws PageUnpinnedException, InvalidFrameNumberException, HashEntryNotFoundException, ReplacerException {
		if (headerPage != null) {
			SystemDefs.JavabaseBM.unpinPage(headerPageId, true);
			headerPage = null;
		}
	}

	/**
	 * Destroy entire B+ tree file.
	 * 
	 * @exception IOException
	 *                error from the lower layer
	 * @exception IteratorException
	 *                iterator error
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception FreePageException
	 *                error when free a page
	 * @exception DeleteFileEntryException
	 *                failed when delete a file from DM
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                failed when pin a page
	 */
	public void destroyFile() throws IOException, IteratorException, UnpinPageException, FreePageException,
			DeleteFileEntryException, ConstructPageException, PinPageException {
		if (headerPage != null) {
			PageId pgId = headerPage.get_rootId();
			if (pgId.pid != INVALID_PAGE)
				_destroyFile(pgId);
			unpinPage(headerPageId);
			freePage(headerPageId);
			delete_file_entry(dbname);
			headerPage = null;
		}
	}

	private void _destroyFile(PageId pageno) throws IOException, IteratorException, PinPageException,
			ConstructPageException, UnpinPageException, FreePageException {

		BTSortedPage sortedPage;
		Page page = pinPage(pageno);
		sortedPage = new BTSortedPage(page, headerPage.get_keyType());

		if (sortedPage.getType() == NodeType.INDEX) {
			BTIndexPage indexPage = new BTIndexPage(page, headerPage.get_keyType());
			RID rid = new RID();
			PageId childId;
			KeyDataEntry entry;
			for (entry = indexPage.getFirst(rid); entry != null; entry = indexPage.getNext(rid)) {
				childId = ((IndexData) (entry.data)).getData();
				_destroyFile(childId);
			}
		} else { // BTLeafPage

			unpinPage(pageno);
			freePage(pageno);
		}

	}

	private void updateHeader(PageId newRoot) throws IOException, PinPageException, UnpinPageException {

		BTreeHeaderPage header;
		PageId old_data;

		header = new BTreeHeaderPage(pinPage(headerPageId));

		//old_data = headerPage.get_rootId();
		header.set_rootId(newRoot);

		// clock in dirty bit to bm so our dtor needn't have to worry about it
		unpinPage(headerPageId, true /* = DIRTY */ );

		// ASSERTIONS:
		// - headerPage, headerPageId valid, pinned and marked as dirty

	}

	/**
	 * insert record with the given key and rid
	 * 
	 * @param key
	 *            the key of the record. Input parameter.
	 * @param rid
	 *            the rid of the record. Input parameter.
	 * @exception KeyTooLongException
	 *                key size exceeds the max keysize.
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IOException
	 *                error from the lower layer
	 * @exception LeafInsertRecException
	 *                insert error in leaf page
	 * @exception IndexInsertRecException
	 *                insert error in index page
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception NodeNotMatchException
	 *                node not match index page nor leaf page
	 * @exception ConvertException
	 *                error when convert between revord and byte array
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error when search
	 * @exception IteratorException
	 *                iterator error
	 * @exception LeafDeleteException
	 *                error when delete in leaf page
	 * @exception InsertException
	 *                error when insert in index page
	 */
	public void insert(KeyClass key, RID rid) throws KeyTooLongException, KeyNotMatchException, LeafInsertRecException,
			IndexInsertRecException, ConstructPageException, UnpinPageException, PinPageException,
			NodeNotMatchException, ConvertException, DeleteRecException, IndexSearchException, IteratorException,
			LeafDeleteException, InsertException, IOException

	{
		KeyDataEntry newRootEntry;

		if (BT.getKeyLength(key) > headerPage.get_maxKeySize())
			throw new KeyTooLongException(null, "");

		if (key instanceof StringKey) {
			if (headerPage.get_keyType() != AttrType.attrString) {
				throw new KeyNotMatchException(null, "");
			}
		} else if (key instanceof IntegerKey) {
			if (headerPage.get_keyType() != AttrType.attrInteger) {
				throw new KeyNotMatchException(null, "");
			}
		} else
			throw new KeyNotMatchException(null, "");

		// TWO CASES:
		// 1. headerPage.root == INVALID_PAGE:
		// - the tree is empty and we have to create a new first page;
		// this page will be a leaf page
		// 2. headerPage.root != INVALID_PAGE:
		// - we call _insert() to insert the pair (key, rid)

		if (trace != null) {
			trace.writeBytes("INSERT " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
			trace.writeBytes("DO" + lineSep);
			trace.flush();
		}

		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			PageId newRootPageId;
			BTLeafPage newRootPage;
			RID dummyrid;

			newRootPage = new BTLeafPage(headerPage.get_keyType());
			newRootPageId = newRootPage.getCurPage();

			if (trace != null) {
				trace.writeBytes("NEWROOT " + newRootPageId + lineSep);
				trace.flush();
			}

			newRootPage.setNextPage(new PageId(INVALID_PAGE));
			newRootPage.setPrevPage(new PageId(INVALID_PAGE));

			// ASSERTIONS:
			// - newRootPage, newRootPageId valid and pinned

			newRootPage.insertRecord(key, rid);

			if (trace != null) {
				trace.writeBytes("PUTIN node " + newRootPageId + lineSep);
				trace.flush();
			}

			unpinPage(newRootPageId, true); /* = DIRTY */
			updateHeader(newRootPageId);

			if (trace != null) {
				trace.writeBytes("DONE" + lineSep);
				trace.flush();
			}

			return;
		}

		// ASSERTIONS:
		// - headerPageId, headerPage valid and pinned
		// - headerPage.root holds the pageId of the root of the B-tree
		// - none of the pages of the tree is pinned yet

		if (trace != null) {
			trace.writeBytes("SEARCH" + lineSep);
			trace.flush();
		}

		newRootEntry = _insert(key, rid, headerPage.get_rootId());

		// TWO CASES:
		// - newRootEntry != null: a leaf split propagated up to the root
		// and the root split: the new pageNo is in
		// newChildEntry.data.pageNo
		// - newRootEntry == null: no new root was created;
		// information on headerpage is still valid

		// ASSERTIONS:
		// - no page pinned

		if (newRootEntry != null) {
			BTIndexPage newRootPage;
			PageId newRootPageId;
			Object newEntryKey;

			// the information about the pair <key, PageId> is
			// packed in newRootEntry: extract it

			newRootPage = new BTIndexPage(headerPage.get_keyType());
			newRootPageId = newRootPage.getCurPage();

			// ASSERTIONS:
			// - newRootPage, newRootPageId valid and pinned
			// - newEntryKey, newEntryPage contain the data for the new entry
			// which was given up from the level down in the recursion

			if (trace != null) {
				trace.writeBytes("NEWROOT " + newRootPageId + lineSep);
				trace.flush();
			}

			newRootPage.insertKey(newRootEntry.key, ((IndexData) newRootEntry.data).getData());

			// the old root split and is now the left child of the new root
			newRootPage.setPrevPage(headerPage.get_rootId());

			unpinPage(newRootPageId, true /* = DIRTY */);

			updateHeader(newRootPageId);

		}

		if (trace != null) {
			trace.writeBytes("DONE" + lineSep);
			trace.flush();
		}

		return;
	}

	private KeyDataEntry _insert(KeyClass key, RID rid, PageId currentPageId)
			throws PinPageException, IOException, ConstructPageException, LeafDeleteException, ConstructPageException,
			DeleteRecException, IndexSearchException, UnpinPageException, LeafInsertRecException, ConvertException,
			IteratorException, IndexInsertRecException, KeyNotMatchException, NodeNotMatchException, InsertException

	{
		BTSortedPage currentPage;
		Page page;
		KeyDataEntry upEntry;

		page = pinPage(currentPageId);
		currentPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + currentPageId + lineSep);
			trace.flush();
		}

		// TWO CASES:
		// - pageType == INDEX:
		// recurse and then split if necessary
		// - pageType == LEAF:
		// try to insert pair (key, rid), maybe split

		if (currentPage.getType() == NodeType.INDEX) {
			BTIndexPage currentIndexPage = new BTIndexPage(page, headerPage.get_keyType());
			PageId currentIndexPageId = currentPageId;
			PageId nextPageId;

			nextPageId = currentIndexPage.getPageNoByKey(key);

			// now unpin the page, recurse and then pin it again
			unpinPage(currentIndexPageId);

			upEntry = _insert(key, rid, nextPageId);

			// two cases:
			// - upEntry == null: one level lower no split has occurred:
			// we are done.
			// - upEntry != null: one of the children has split and
			// upEntry is the new data entry which has
			// to be inserted on this index page

			if (upEntry == null)
				return null;

			currentIndexPage = new BTIndexPage(pinPage(currentPageId), headerPage.get_keyType());

			// ASSERTIONS:
			// - upEntry != null
			// - currentIndexPage, currentIndexPageId valid and pinned

			// the information about the pair <key, PageId> is
			// packed in upEntry

			// check whether there can still be entries inserted on that page
			if (currentIndexPage.available_space() >= BT.getKeyDataLength(upEntry.key, NodeType.INDEX)) {

				// no split has occurred
				currentIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());

				unpinPage(currentIndexPageId, true /* DIRTY */);

				return null;
			}

			// ASSERTIONS:
			// - on the current index page is not enough space available .
			// it splits

			// therefore we have to allocate a new index page and we will
			// distribute the entries
			// - currentIndexPage, currentIndexPageId valid and pinned

			BTIndexPage newIndexPage;
			PageId newIndexPageId;

			// we have to allocate a new INDEX page and
			// to redistribute the index entries
			newIndexPage = new BTIndexPage(headerPage.get_keyType());
			newIndexPageId = newIndexPage.getCurPage();

			if (trace != null) {
				if (headerPage.get_rootId().pid != currentIndexPageId.pid)
					trace.writeBytes("SPLIT node " + currentIndexPageId + " IN nodes " + currentIndexPageId + " "
							+ newIndexPageId + lineSep);
				else
					trace.writeBytes("ROOTSPLIT IN nodes " + currentIndexPageId + " " + newIndexPageId + lineSep);
				trace.flush();
			}

			// ASSERTIONS:
			// - newIndexPage, newIndexPageId valid and pinned
			// - currentIndexPage, currentIndexPageId valid and pinned
			// - upEntry containing (Key, Page) for the new entry which was
			// given up from the level down in the recursion

			KeyDataEntry tmpEntry;
			PageId tmpPageId;
			RID insertRid;
			RID delRid = new RID();

			for (tmpEntry = currentIndexPage.getFirst(delRid); tmpEntry != null; tmpEntry = currentIndexPage
					.getFirst(delRid)) {
				newIndexPage.insertKey(tmpEntry.key, ((IndexData) tmpEntry.data).getData());
				currentIndexPage.deleteSortedRecord(delRid);
			}

			// ASSERTIONS:
			// - currentIndexPage empty
			// - newIndexPage holds all former records from currentIndexPage

			// we will try to make an equal split
			RID firstRid = new RID();
			KeyDataEntry undoEntry = null;
			for (tmpEntry = newIndexPage.getFirst(firstRid); (currentIndexPage.available_space() > newIndexPage
					.available_space()); tmpEntry = newIndexPage.getFirst(firstRid)) {
				// now insert the <key,pageId> pair on the new
				// index page
				undoEntry = tmpEntry;
				currentIndexPage.insertKey(tmpEntry.key, ((IndexData) tmpEntry.data).getData());
				newIndexPage.deleteSortedRecord(firstRid);
			}

			// undo the final record
			if (currentIndexPage.available_space() < newIndexPage.available_space()) {

				newIndexPage.insertKey(undoEntry.key, ((IndexData) undoEntry.data).getData());

				currentIndexPage.deleteSortedRecord(
						new RID(currentIndexPage.getCurPage(), (int) currentIndexPage.getSlotCnt() - 1));
			}

			// check whether <newKey, newIndexPageId>
			// will be inserted
			// on the newly allocated or on the old index page

			tmpEntry = newIndexPage.getFirst(firstRid);

			if (BT.keyCompare(upEntry.key, tmpEntry.key) >= 0) {
				// the new data entry belongs on the new index page
				newIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());
			} else {
				currentIndexPage.insertKey(upEntry.key, ((IndexData) upEntry.data).getData());

				// int i= (int)currentIndexPage.getSlotCnt()-1;
				// tmpEntry =BT.getEntryFromBytes(currentIndexPage.getpage(),
				// currentIndexPage.getSlotOffset(i),
				// currentIndexPage.getSlotLength(i),
				// headerPage.get_keyType(),NodeType.INDEX);

				// newIndexPage.insertKey( tmpEntry.key,
				// ((IndexData)tmpEntry.data).getData());

				// currentIndexPage.deleteSortedRecord
				// (new RID(currentIndexPage.getCurPage(), i) );

			}

			unpinPage(currentIndexPageId, true /* dirty */);

			// fill upEntry
			upEntry = newIndexPage.getFirst(delRid);

			// now set prevPageId of the newIndexPage to the pageId
			// of the deleted entry:
			newIndexPage.setPrevPage(((IndexData) upEntry.data).getData());

			// delete first record on new index page since it is given up
			newIndexPage.deleteSortedRecord(delRid);

			unpinPage(newIndexPageId, true /* dirty */);

			if (trace != null) {
				trace_children(currentIndexPageId);
				trace_children(newIndexPageId);
			}

			((IndexData) upEntry.data).setData(newIndexPageId);

			return upEntry;

			// ASSERTIONS:
			// - no pages pinned
			// - upEntry holds the pointer to the KeyDataEntry which is
			// to be inserted on the index page one level up

		}

		else if (currentPage.getType() == NodeType.LEAF) {
			BTLeafPage currentLeafPage = new BTLeafPage(page, headerPage.get_keyType());

			PageId currentLeafPageId = currentPageId;

			// ASSERTIONS:
			// - currentLeafPage, currentLeafPageId valid and pinned

			// check whether there can still be entries inserted on that page
			if (currentLeafPage.available_space() >= BT.getKeyDataLength(key, NodeType.LEAF)) {
				// no split has occurred

				currentLeafPage.insertRecord(key, rid);

				unpinPage(currentLeafPageId, true /* DIRTY */);

				if (trace != null) {
					trace.writeBytes("PUTIN node " + currentLeafPageId + lineSep);
					trace.flush();
				}

				return null;
			}

			// ASSERTIONS:
			// - on the current leaf page is not enough space available.
			// It splits.
			// - therefore we have to allocate a new leaf page and we will
			// - distribute the entries

			BTLeafPage newLeafPage;
			PageId newLeafPageId;
			// we have to allocate a new LEAF page and
			// to redistribute the data entries entries
			newLeafPage = new BTLeafPage(headerPage.get_keyType());
			newLeafPageId = newLeafPage.getCurPage();

			newLeafPage.setNextPage(currentLeafPage.getNextPage());
			newLeafPage.setPrevPage(currentLeafPageId); // for dbl-linked list
			currentLeafPage.setNextPage(newLeafPageId);

			// change the prevPage pointer on the next page:
			// PageId rightPageId;
			// rightPageId = newLeafPage.getNextPage();

			// if (rightPageId.pid != INVALID_PAGE)
			// {
			// System.out.println(rightPageId.pid);
			// BTLeafPage rightPage;
			// rightPage=new BTLeafPage(rightPageId, headerPage.get_keyType());

			// rightPage.setPrevPage(newLeafPageId);
			// unpinPage(rightPageId, true /* = DIRTY */);

			// ASSERTIONS:
			// - newLeafPage, newLeafPageId valid and pinned
			// - currentLeafPage, currentLeafPageId valid and pinned
			// }

			if (trace != null) {
				if (headerPage.get_rootId().pid != currentLeafPageId.pid)
					trace.writeBytes("SPLIT node " + currentLeafPageId + " IN nodes " + currentLeafPageId + " "
							+ newLeafPageId + lineSep);
				else
					trace.writeBytes("ROOTSPLIT IN nodes " + currentLeafPageId + " " + newLeafPageId + lineSep);
				trace.flush();
			}

			KeyDataEntry tmpEntry;
			RID firstRid = new RID();

			for (tmpEntry = currentLeafPage.getFirst(firstRid); tmpEntry != null; tmpEntry = currentLeafPage
					.getFirst(firstRid)) {

				newLeafPage.insertRecord(tmpEntry.key, ((LeafData) (tmpEntry.data)).getData());
				currentLeafPage.deleteSortedRecord(firstRid);

			}

			// ASSERTIONS:
			// - currentLeafPage empty
			// - newLeafPage holds all former records from currentLeafPage

			KeyDataEntry undoEntry = null;
			for (tmpEntry = newLeafPage.getFirst(firstRid); newLeafPage.available_space() < currentLeafPage
					.available_space(); tmpEntry = newLeafPage.getFirst(firstRid)) {
				undoEntry = tmpEntry;
				currentLeafPage.insertRecord(tmpEntry.key, ((LeafData) tmpEntry.data).getData());
				newLeafPage.deleteSortedRecord(firstRid);
			}

			if (BT.keyCompare(key, undoEntry.key) < 0) {
				// undo the final record
				if (currentLeafPage.available_space() < newLeafPage.available_space()) {
					newLeafPage.insertRecord(undoEntry.key, ((LeafData) undoEntry.data).getData());

					currentLeafPage.deleteSortedRecord(
							new RID(currentLeafPage.getCurPage(), (int) currentLeafPage.getSlotCnt() - 1));
				}
			}

			// check whether <key, rid>
			// will be inserted
			// on the newly allocated or on the old leaf page

			if (BT.keyCompare(key, undoEntry.key) >= 0) {
				// the new data entry belongs on the new Leaf page
				newLeafPage.insertRecord(key, rid);

				if (trace != null) {
					trace.writeBytes("PUTIN node " + newLeafPageId + lineSep);
					trace.flush();
				}

			} else {
				currentLeafPage.insertRecord(key, rid);
			}

			unpinPage(currentLeafPageId, true /* dirty */);

			if (trace != null) {
				trace_children(currentLeafPageId);
				trace_children(newLeafPageId);
			}

			// fill upEntry
			tmpEntry = newLeafPage.getFirst(firstRid);
			upEntry = new KeyDataEntry(tmpEntry.key, newLeafPageId);

			unpinPage(newLeafPageId, true /* dirty */);

			// ASSERTIONS:
			// - no pages pinned
			// - upEntry holds the valid KeyDataEntry which is to be inserted
			// on the index page one level up
			return upEntry;
		} else {
			throw new InsertException(null, "");
		}
	}

	/**
	 * delete leaf entry given its <key, rid> pair. `rid' is IN the data entry;
	 * it is not the id of the data entry)
	 * 
	 * @param key
	 *            the key in pair <key, rid>. Input Parameter.
	 * @param rid
	 *            the rid in pair <key, rid>. Input Parameter.
	 * @return true if deleted. false if no such record.
	 * @exception DeleteFashionException
	 *                neither full delete nor naive delete
	 * @exception LeafRedistributeException
	 *                redistribution error in leaf pages
	 * @exception RedistributeException
	 *                redistribution error in index pages
	 * @exception InsertRecException
	 *                error when insert in index page
	 * @exception KeyNotMatchException
	 *                key is neither integer key nor string key
	 * @exception UnpinPageException
	 *                error when unpin a page
	 * @exception IndexInsertRecException
	 *                error when insert in index page
	 * @exception FreePageException
	 *                error in BT page constructor
	 * @exception RecordNotFoundException
	 *                error delete a record in a BT page
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception IndexFullDeleteException
	 *                fill delete error
	 * @exception LeafDeleteException
	 *                delete error in leaf page
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception DeleteRecException
	 *                error when delete in index page
	 * @exception IndexSearchException
	 *                error in search in index pages
	 * @exception IOException
	 *                error from the lower layer
	 *
	 */
	public boolean Delete(KeyClass key, RID rid)
			throws DeleteFashionException, LeafRedistributeException, RedistributeException, InsertRecException,
			KeyNotMatchException, UnpinPageException, IndexInsertRecException, FreePageException,
			RecordNotFoundException, PinPageException, IndexFullDeleteException, LeafDeleteException, IteratorException,
			ConstructPageException, DeleteRecException, IndexSearchException, IOException {
		if (headerPage.get_deleteFashion() == DeleteFashion.FULL_DELETE)
			try {
				return FullDelete(key, rid);
			} catch (LeafInsertRecException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
				return false;
			}
		else if (headerPage.get_deleteFashion() == DeleteFashion.NAIVE_DELETE)
			try {
				return NaiveDelete(key, rid);
			} catch (LeafInsertRecException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
		else
			throw new DeleteFashionException(null, "");
	}

	/*
	 * findRunStart. Status BTreeFile::findRunStart (const void lo_key, RID
	 * *pstartrid)
	 *
	 * find left-most occurrence of `lo_key', going all the way left if lo_key
	 * is null.
	 * 
	 * Starting record returned in *pstartrid, on page *pppage, which is pinned.
	 *
	 * Since we allow duplicates, this must "go left" as described in the text
	 * (for the search algorithm).
	 * 
	 * @param lo_key find left-most occurrence of `lo_key', going all the way
	 * left if lo_key is null.
	 * 
	 * @param startrid it will reurn the first rid =< lo_key
	 * 
	 * @return return a BTLeafPage instance which is pinned. null if no key was
	 * found.
	 */

	BTLeafPage findRunStart(KeyClass lo_key, RID startrid) throws IOException, IteratorException, KeyNotMatchException,
			ConstructPageException, PinPageException, UnpinPageException {
		BTLeafPage pageLeaf;
		BTIndexPage pageIndex;
		Page page;
		BTSortedPage sortPage;
		PageId pageno;
		PageId curpageno = null; // iterator
		PageId prevpageno;
		PageId nextpageno;
		RID curRid;
		KeyDataEntry curEntry;
		KeyDataEntry prevEntry = null;

		pageno = headerPage.get_rootId();

		if (pageno.pid == INVALID_PAGE) { // no pages in the BTREE
			pageLeaf = null; // should be handled by
			// startrid =INVALID_PAGEID ; // the caller
			return pageLeaf;
		}

		page = pinPage(pageno);
		sortPage = new BTSortedPage(page, headerPage.get_keyType());

		if (trace != null) {
			trace.writeBytes("VISIT node " + pageno + lineSep);
			trace.flush();
		}

		// ASSERTION
		// - pageno and sortPage is the root of the btree
		// - pageno and sortPage valid and pinned

		while (sortPage.getType() == NodeType.INDEX) {
			pageIndex = new BTIndexPage(page, headerPage.get_keyType());
			prevpageno = pageIndex.getPrevPage();
			curEntry = pageIndex.getFirst(startrid);
			while (curEntry != null && lo_key != null && BT.keyCompare(curEntry.key, lo_key) < 0) {
				prevEntry = curEntry;
				prevpageno = ((IndexData) curEntry.data).getData();
				curEntry = pageIndex.getNext(startrid);
			}

			unpinPage(pageno);

			pageno = prevpageno;
			page = pinPage(pageno);
			sortPage = new BTSortedPage(page, headerPage.get_keyType());

			if (trace != null) {
				trace.writeBytes("VISIT node " + pageno + lineSep);
				trace.flush();
			}

		}

		pageLeaf = new BTLeafPage(page, headerPage.get_keyType());

		curEntry = pageLeaf.getFirst(startrid);
		while (curEntry == null) {
			// skip empty leaf pages off to left
			nextpageno = pageLeaf.getNextPage();
			unpinPage(pageno);
			if (nextpageno.pid == INVALID_PAGE) {
				// oops, no more records, so set this scan to indicate this.
				return null;
			}

			pageno = nextpageno;
			pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());
			curEntry = pageLeaf.getFirst(startrid);
		}

		// ASSERTIONS:
		// - curkey, curRid: contain the first record on the
		// current leaf page (curkey its key, cur
		// - pageLeaf, pageno valid and pinned

		if (lo_key == null) {
			return pageLeaf;
			// note that pageno/pageLeaf is still pinned;
			// scan will unpin it when done
		}

		while (BT.keyCompare(curEntry.key, lo_key) < 0) {
			curEntry = pageLeaf.getNext(startrid);
			while (curEntry == null) { // have to go right
				nextpageno = pageLeaf.getNextPage();
				unpinPage(pageno);

				if (nextpageno.pid == INVALID_PAGE) {
					return null;
				}

				pageno = nextpageno;
				pageLeaf = new BTLeafPage(pinPage(pageno), headerPage.get_keyType());

				curEntry = pageLeaf.getFirst(startrid);
			}
		}
		return pageLeaf;
	}

	/*
	 * Status BTreeFile::NaiveDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 *
	 * We don't do merging or redistribution, but do allow duplicates.
	 *
	 * Page containing first occurrence of key `key' is found for us by
	 * findRunStart. We then iterate for (just a few) pages, if necesary, to
	 * find the one containing <key,rid>, which we then delete via
	 * BTLeafPage::delUserRid.
	 */

	private boolean NaiveDelete(KeyClass key, RID rid)
			throws LeafDeleteException, KeyNotMatchException, PinPageException, ConstructPageException, IOException,
			UnpinPageException, PinPageException, IndexSearchException, IteratorException, LeafRedistributeException, LeafInsertRecException, DeleteRecException {
		BTLeafPage leafPage;
		RID curRid = new RID(); // iterator
		KeyClass curkey;
		RID dummyRid;
		PageId nextpage;
		boolean deleted;
		KeyDataEntry entry;
		if (trace != null) {
			trace.writeBytes("DELETE " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
			trace.writeBytes("DO" + lineSep);
			trace.writeBytes("SEARCH" + lineSep);
			trace.flush();
		}

		leafPage = findRunStart(key, curRid); // find first page,rid of key
		if (leafPage == null)
			return false;

		entry = leafPage.getCurrent(curRid);

		while (true) {

			while (entry == null) { // have to go right
				nextpage = leafPage.getNextPage();
				unpinPage(leafPage.getCurPage());
				if (nextpage.pid == INVALID_PAGE) {
					return false;
				}

				leafPage = new BTLeafPage(pinPage(nextpage), headerPage.get_keyType());
				entry = leafPage.getFirst(new RID());
			}

			if (BT.keyCompare(key, entry.key) > 0)
				break;

			if (leafPage.delEntry(new KeyDataEntry(key, rid)) == true) {

				// successfully found <key, rid> on this page and deleted it.
				// unpin dirty page and return OK.
				unpinPage(leafPage.getCurPage(), true /* = DIRTY */);

				if (trace != null) {
					trace.writeBytes("TAKEFROM node " + leafPage.getCurPage() + lineSep);
					trace.writeBytes("DONE" + lineSep);
					trace.flush();
				}

				return true;
			}

			nextpage = leafPage.getNextPage();
			unpinPage(leafPage.getCurPage());

			leafPage = new BTLeafPage(pinPage(nextpage), headerPage.get_keyType());

			entry = leafPage.getFirst(curRid);
		}

		/*
		 * We reached a page with first key > `key', so return an error. We
		 * should have got true back from delUserRid above. Apparently the
		 * specified <key,rid> data entry does not exist.
		 */

		unpinPage(leafPage.getCurPage());
		return false;
	}

	
	/*
	 * Status BTreeFile::FullDelete (const void *key, const RID rid)
	 * 
	 * Remove specified data entry (<key, rid>) from an index.
	 *
	 * Most work done recursively by _Delete
	 *
	 * Special case: delete root if the tree is empty
	 *
	 * Page containing first occurrence of key `key' is found for us After the
	 * page containing first occurence of key 'key' is found, we iterate for
	 * (just a few) pages, if necesary, to find the one containing <key,rid>,
	 * which we then delete via BTLeafPage::delUserRid.
	 * 
	 * @return false if no such record; true if succees
	 */

	private boolean FullDelete(KeyClass key, RID rid)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, IOException,
			IteratorException, PinPageException, UnpinPageException, IteratorException, LeafInsertRecException {

		try {

			if (trace != null) {
				trace.writeBytes("DELETE " + rid.pageNo + " " + rid.slotNo + " " + key + lineSep);
				trace.writeBytes("DO" + lineSep);
				trace.writeBytes("SEARCH" + lineSep);
				trace.flush();
			}
			if(headerPage.get_rootId().pid!=-1){
				_Delete(key, rid, headerPage.get_rootId(), null);
			}
			else{
				System.out.println("Tree is empty");
				return false;
			}

			if (trace != null) {
				trace.writeBytes("DONE" + lineSep);
				trace.flush();
			}

			return true;
		} catch (RecordNotFoundException e) {
			e.printStackTrace();
			return false;
		}

	}

	/**
	 * delete_leaf method will traverse the page to find the key.once the key is
	 * found , key will be deleted.if the leaf page underflows based on the
	 * given occupancy criteria , either redistribution or merge will happen to
	 * balance the Tree.If merge happens , it will return merge key which needs
	 * to be deleted in parent page or this method will return null in all other
	 * case
	 * 
	 * @param currentPage
	 *            the page where key is supposed to be.
	 * @param key
	 *            key need to be deleted
	 * @param rid
	 *            rid of the key to be deleted
	 * @param currentPageId
	 *            page id of the page where key is supposed to be
	 * @param parentPageId
	 *            page id of the currents's parent page
	 * 
	 * @return key_class merge key which needs to be deleted in current's parent
	 *         page
	 * 
	 * @throws KeyNotMatchException
	 * @throws LeafDeleteException
	 * @throws ConstructPageException
	 * @throws IOException
	 * @throws IteratorException
	 * @throws PinPageException
	 * @throws LeafInsertRecException
	 * @throws DeleteRecException
	 * @throws LeafRedistributeException
	 * @throws UnpinPageException
	 * @throws IndexFullDeleteException
	 * @throws RedistributeException
	 * @throws IndexInsertRecException
	 * @throws IndexSearchException
	 * @throws FreePageException 
	 * @throws RecordNotFoundException 
	 */
	private KeyClass deleteLeaf(Page currentPage, KeyClass key, RID rid, PageId currentPageId, PageId parentPageId)
			throws KeyNotMatchException, LeafDeleteException, ConstructPageException, IOException, IteratorException,
			PinPageException, LeafInsertRecException, DeleteRecException, LeafRedistributeException, UnpinPageException,
			IndexFullDeleteException, RedistributeException, IndexInsertRecException, IndexSearchException,
			FreePageException, RecordNotFoundException {
		BTLeafPage leafPage = new BTLeafPage(currentPage, headerPage.get_keyType());
		
		KeyDataEntry tmpEntry = leafPage.getFirst(new RID());
		
		while ((tmpEntry != null) && (BT.keyCompare(key, tmpEntry.key) >= 0)) {
			if (leafPage.delEntry(new KeyDataEntry(key, rid))) {
				if(leafPage.getCurPage().pid==headerPage.get_rootId().pid){
					if(leafPage.numberOfRecords()!=0){
						unpinPage(leafPage.getCurPage(),true);
						return null;
					}else{
						unpinPage(leafPage.getCurPage());
						freePage(leafPage.getCurPage());
						updateHeader(new PageId(INVALID_PAGE));
						return null;
					}
				}
				else if((PAGE_SIZE-leafPage.available_space())<(int)(PAGE_SIZE*(PERCENTAGE/100.0f))){
					return dataRearrangement(currentPage,currentPageId, parentPageId, key);
				}else{
					unpinPage(leafPage.getCurPage(), true);
					return null;
				}
			}
			tmpEntry = leafPage.getNext(rid);
		}
		unpinPage(currentPageId, false);
		throw new RecordNotFoundException("Key is not found in the B+ Tree "+key);
	}

	/**
	 * delete_index method will be called recursively until current page is of
	 * type index. Once delete_leaf method deletes the key and returns the merge
	 * key which needs to be deleted in the index page , delete index will
	 * delete exact merge key if it matches in index page or first key which is
	 * greater than merge key in parent index page.After key in the index page
	 * is deleted , if page occupancy falls below the occupancy criteria ,
	 * either redistribution or merge will happen to balance the Tree.If merge
	 * happens , it will return merge key which needs to be deleted in parent
	 * page will be returned or this method will return null in all other case
	 * 
	 * @param currentPage
	 *            the page where key is supposed to be.
	 * @param key
	 *            key need to be deleted
	 * @param rid
	 *            rid of the key to be deleted
	 * @param currentPageId
	 *            page id of the page where key is supposed to be
	 * @param parentPageId
	 *            page id of the currents's parent page
	 * 
	 * @return key_class merge key which needs to be deleted in current's parent
	 *         page
	 * 
	 * @throws ConstructPageException
	 * @throws IOException
	 * @throws IndexSearchException
	 * @throws PinPageException
	 * @throws IndexFullDeleteException
	 * @throws LeafInsertRecException
	 * @throws DeleteRecException
	 * @throws IteratorException
	 * @throws LeafRedistributeException
	 * @throws UnpinPageException
	 * @throws RedistributeException
	 * @throws IndexInsertRecException
	 * @throws KeyNotMatchException
	 * @throws LeafDeleteException
	 * @throws RecordNotFoundException
	 * @throws InsertRecException
	 * @throws FreePageException
	 */
	private KeyClass deleteIndex(Page currentPage, KeyClass key, RID rid, PageId currentPageId, PageId parentPageId)
			throws ConstructPageException, IOException, IndexSearchException, PinPageException,
			IndexFullDeleteException, LeafInsertRecException, DeleteRecException, IteratorException,
			LeafRedistributeException, UnpinPageException, RedistributeException, IndexInsertRecException,
			KeyNotMatchException, LeafDeleteException, RecordNotFoundException, InsertRecException, FreePageException {
		BTIndexPage pageIndex = new BTIndexPage(currentPage, headerPage.get_keyType());
		PageId nextPageToFind = pageIndex.getPageNoByKey(key);
		unpinPage(currentPageId);
		KeyClass deleteUpEntry = _Delete(key, rid, nextPageToFind, currentPageId);
		//pinning the index page again since _Delete might have un pinned the page
		pageIndex = new BTIndexPage(pinPage(currentPageId), headerPage.get_keyType());
		if(deleteUpEntry!=null){
			//deleteKey method will delete exact key if found or key which is less than parameter 
			RID curRid = pageIndex.deleteKey(deleteUpEntry);
			//If the index page is root page then occupancy should not be checked
			if(pageIndex.getCurPage().pid==headerPage.get_rootId().pid){
				if(pageIndex.numberOfRecords()!=0){
					unpinPage(pageIndex.getCurPage(),true);
					return null;
				}else{
					BTSortedPage childPage = new BTSortedPage(pageIndex.getPrevPage(), headerPage.get_keyType());
					updateHeader(pageIndex.getPrevPage());
					unpinPage(childPage.getCurPage());
					unpinPage(pageIndex.getCurPage());
					freePage(pageIndex.getCurPage());
					return null;
				}
			}
			if((PAGE_SIZE-pageIndex.available_space())<(int)(PAGE_SIZE*(PERCENTAGE/100.0f))){
				return dataRearrangement(currentPage,currentPageId, parentPageId, key);
			}else{
				unpinPage(pageIndex.getCurPage(),false);
				return null;
			}
		}else{
			unpinPage(pageIndex.getCurPage(), true);
		}
		return null;
	}

	/**
	 * _Delete method will be recursively called until the key is found in the
	 * leaf page.once the leaf page data is redistributed or merged, recursion
	 * will unfold, by redistributing the immediate parent page until it reaches
	 * root page or currents page satisfies the page occupancy criteria.
	 * 
	 * @param key
	 *            key need to be deleted
	 * @param rid
	 *            rid of the key to be deleted
	 * @param currentPageId
	 *            page id of the page where key is supposed to be
	 * @param parentPageId
	 *            page id of the currents's parent page
	 * 
	 * @return key_class-merge key which needs to be deleted in current's parent
	 *         page
	 * 
	 * @throws IndexInsertRecException
	 * @throws RedistributeException
	 * @throws IndexSearchException
	 * @throws RecordNotFoundException
	 * @throws DeleteRecException
	 * @throws InsertRecException
	 * @throws LeafRedistributeException
	 * @throws IndexFullDeleteException
	 * @throws FreePageException
	 * @throws LeafDeleteException
	 * @throws KeyNotMatchException
	 * @throws ConstructPageException
	 * @throws UnpinPageException
	 * @throws IteratorException
	 * @throws PinPageException
	 * @throws IOException
	 * @throws LeafInsertRecException
	 */
	private KeyClass _Delete(KeyClass key, RID rid, PageId currentPageId, PageId parentPageId)
			throws IndexInsertRecException, RedistributeException, IndexSearchException, RecordNotFoundException,
			DeleteRecException, InsertRecException, LeafRedistributeException, IndexFullDeleteException,
			FreePageException, LeafDeleteException, KeyNotMatchException, ConstructPageException, UnpinPageException,
			IteratorException, PinPageException, IOException, LeafInsertRecException {

		Page currentPage = pinPage(currentPageId);
		BTSortedPage sortPage = new BTSortedPage(currentPage, headerPage.get_keyType());
		if(sortPage.getType()==NodeType.INDEX){
			return deleteIndex(currentPage,key, rid, currentPageId, parentPageId);
		}else if(sortPage.getType()==NodeType.LEAF){
			return deleteLeaf(currentPage,key, rid, currentPageId, parentPageId);
		}
		return null;
	}

	/**
	 * This method will either redistribute or call merge method by finding the
	 * sibling of the current page and checking for the occupancy criteria.if
	 * the page is redistributed with sibling page , all the required pages will
	 * be un pinned and method will return null
	 * 
	 * @param parentPage
	 *            Page object of the parent page 
	 * @param currentPageId
	 *            page id of the page where key is supposed to be
	 * @param parentPage
	 *            page id of the currents's parent page
	 * @param key
	 *            key need to be deleted
	 * 
	 * @return key_class merge key which needs to be deleted in current's parent
	 *         page
	 * 
	 * @throws ConstructPageException
	 * @throws PinPageException
	 * @throws IOException
	 * @throws LeafInsertRecException
	 * @throws DeleteRecException
	 * @throws IteratorException
	 * @throws LeafRedistributeException
	 * @throws UnpinPageException
	 * @throws IndexFullDeleteException
	 * @throws RedistributeException
	 * @throws IndexInsertRecException
	 * @throws IndexSearchException
	 */
	private KeyClass dataRearrangement(Page currentPage,PageId currentPageId, PageId parentPage, KeyClass key)
			throws ConstructPageException, PinPageException, IOException, LeafInsertRecException, DeleteRecException,
			IteratorException, LeafRedistributeException, UnpinPageException, IndexFullDeleteException,
			RedistributeException, IndexInsertRecException, IndexSearchException {

		BTIndexPage parentIndexPage = new BTIndexPage(pinPage(parentPage), headerPage.get_keyType());
		BTSortedPage sortPage = new BTSortedPage(currentPage, headerPage.get_keyType());
		PageId siblingPageId = null;
		
		boolean redistribute = false;
		KeyClass mergeUpEntry = null;
		int siblingDirection = 0;
		
		if(sortPage.getType()==NodeType.INDEX){
			BTIndexPage currentIndexPage = new BTIndexPage(currentPage, headerPage.get_keyType());
			siblingPageId = new PageId();
			siblingDirection = parentIndexPage.getSibling(key, siblingPageId);
			if(siblingDirection!=0){
				BTIndexPage siblingPage = new BTIndexPage(pinPage(siblingPageId), headerPage.get_keyType());
				System.out.println("Tried index redistributing "+currentIndexPage.getCurPage().pid+" with "+siblingPageId.pid);
				redistribute = siblingPage.redistribute(currentIndexPage, parentIndexPage, siblingDirection, key);
				
				if(!redistribute){
					if(siblingPage.available_space()+currentIndexPage.available_space()>PAGE_SIZE){
						mergeUpEntry = merge(siblingPage, currentIndexPage, siblingDirection,parentIndexPage);
					}else{
						unpinPage(currentIndexPage.getCurPage(), true);
						unpinPage(siblingPageId, true);
						unpinPage(parentIndexPage.getCurPage());
					}
				}
			}else{
				unpinPage(currentIndexPage.getCurPage(), true);
				unpinPage(parentIndexPage.getCurPage());
				return null;
			}
		}else{
			BTLeafPage currentLeafPage = new BTLeafPage(currentPage, headerPage.get_keyType());
			siblingPageId = new PageId();
			siblingDirection = parentIndexPage.getSibling(key, siblingPageId);
			if(siblingDirection!=0){
				BTLeafPage siblingPage = new BTLeafPage(pinPage(siblingPageId), headerPage.get_keyType());
				System.out.println("Tried leaf redistributing "+currentLeafPage.getCurPage().pid+" with "+siblingPageId.pid);
				redistribute = siblingPage.redistribute(currentLeafPage, parentIndexPage, siblingDirection, key);
				if(!redistribute){
					if(siblingPage.available_space()+currentLeafPage.available_space()>PAGE_SIZE){
						mergeUpEntry =  merge(siblingPage, currentLeafPage, siblingDirection,parentIndexPage);
					}
				}
			}else{
				unpinPage(currentLeafPage.getCurPage(), true);
				unpinPage(parentIndexPage.getCurPage());
				return null;
			}
		}
		if(redistribute){
			unpinPage(parentIndexPage.getCurPage(),true);
			unpinPage(currentPageId,true);
			unpinPage(siblingPageId,true);
			return null;
		}
		return mergeUpEntry;
	}

	/**
	 * merge method will merge two index/leaf pages to one and delete the other
	 * page which is empty.if the index page is merged , first key from the
	 * parent is pulled down into the left child page.
	 * 
	 * @param siblingPage
	 *            sibling (left/right) page object of the current page
	 * @param currentPage
	 *            page id of the current page
	 * @param direction
	 *            direction of the sibling to the current page
	 * @param parentIndexPage
	 *            page object of the current's parent page
	 * @return key_class merge key which needs to be deleted in current's parent
	 *         page
	 * @throws LeafInsertRecException
	 * @throws DeleteRecException
	 * @throws IteratorException
	 * @throws IndexInsertRecException
	 * @throws IndexSearchException
	 * @throws IOException
	 * @throws ConstructPageException
	 * @throws UnpinPageException
	 */
	private KeyClass merge(Object siblingPage, Object currentPage, int direction,
			BTIndexPage parentIndexPage) throws LeafInsertRecException, DeleteRecException, IteratorException,
					IndexInsertRecException, IndexSearchException, IOException, ConstructPageException, UnpinPageException {
		if(siblingPage instanceof BTLeafPage && currentPage instanceof BTLeafPage){
			BTLeafPage leftChild, rightChild;
			if(direction==1){
				leftChild = (BTLeafPage)currentPage;
				rightChild = (BTLeafPage)siblingPage;
				
			}else{
				rightChild = (BTLeafPage)currentPage;
				leftChild = (BTLeafPage)siblingPage;
			}
			
			RID mergeRid = new RID();
			KeyClass key = rightChild.getFirst(new RID()).key;
			for (KeyDataEntry tmpEntry = rightChild.getFirst(mergeRid); tmpEntry != null; tmpEntry = rightChild
					.getFirst(mergeRid)) {
				leftChild.insertRecord(tmpEntry.key, ((LeafData) tmpEntry.data).getData());
				rightChild.deleteSortedRecord(mergeRid);
			}
			arrangePointers(leftChild,rightChild);
			try {
				unpinPage(leftChild.getCurPage(), true);
				unpinPage(parentIndexPage.getCurPage(), true);
				unpinPage(rightChild.getCurPage());
				freePage(rightChild.getCurPage());
			} catch (FreePageException e) {
				e.printStackTrace();
			}
			return key;
		}else if (siblingPage instanceof BTIndexPage && currentPage instanceof BTIndexPage){
			
			BTIndexPage leftChild, rightChild;
			if(direction==1){
				leftChild = (BTIndexPage)currentPage;
				rightChild = (BTIndexPage)siblingPage;
				
			}else{
				rightChild = (BTIndexPage)currentPage;
				leftChild = (BTIndexPage)siblingPage;
			}
			
			RID rid = new RID();
			KeyDataEntry keyDataUpEntry = rightChild.getFirst(rid);
			KeyClass findKey = parentIndexPage.findKey(keyDataUpEntry.key);
			leftChild.insertKey(findKey, rightChild.getLeftLink());
			
			RID mergeRid = new RID();
			for (KeyDataEntry tmpEntry =rightChild.getFirst(mergeRid); tmpEntry != null; tmpEntry = rightChild
					.getFirst(mergeRid)) {
				leftChild.insertKey(tmpEntry.key, ((IndexData) tmpEntry.data).getData());
				rightChild.deleteSortedRecord(mergeRid);
			}
			arrangePointers(leftChild,rightChild);
			try {
				unpinPage(leftChild.getCurPage(), true);
				unpinPage(parentIndexPage.getCurPage(), true);
				unpinPage(rightChild.getCurPage());
				freePage(rightChild.getCurPage());
			} catch (FreePageException e) {
				e.printStackTrace();
			}
			return keyDataUpEntry.key;
		}
		return null;
	}

	/**
	 * Since merge will delete the page , this method will arrange pointers
	 * between left page and right page of the deleted page.
	 * 
	 * @param leftChild
	 *            page object of the parent page's left child
	 * @param rightChild
	 *            page object of the parents page's right child
	 * 
	 * @throws IOException
	 * @throws ConstructPageException
	 * @throws UnpinPageException
	 */
	private void arrangePointers(Object leftChild, Object rightChild)
			throws IOException, ConstructPageException, UnpinPageException {
		if(leftChild instanceof BTLeafPage && rightChild instanceof BTLeafPage){
			((BTLeafPage)leftChild).setNextPage(((BTLeafPage)rightChild).getNextPage());
			if (((BTLeafPage)rightChild).getNextPage().pid != INVALID_PAGE) {
				BTLeafPage nextLeafPage = new BTLeafPage(((BTLeafPage)rightChild).getNextPage(),
						headerPage.get_keyType());
				nextLeafPage.setPrevPage(((BTLeafPage)leftChild).getCurPage());
				unpinPage(nextLeafPage.getCurPage(), true);
			}
		}
		else if(leftChild instanceof BTIndexPage && rightChild instanceof BTIndexPage){
			((BTIndexPage)leftChild).setNextPage(((BTIndexPage)rightChild).getNextPage());
			if (((BTIndexPage)rightChild).getNextPage().pid != INVALID_PAGE) {
				BTIndexPage nextLeafPage = new BTIndexPage(((BTIndexPage)rightChild).getNextPage(),
						headerPage.get_keyType());
				nextLeafPage.setPrevPage(((BTIndexPage)leftChild).getCurPage());
				unpinPage(nextLeafPage.getCurPage(), true);
			}
		}
	}

	/**
	 * create a scan with given keys Cases: (1) lo_key = null, hi_key = null
	 * scan the whole index (2) lo_key = null, hi_key!= null range scan from min
	 * to the hi_key (3) lo_key!= null, hi_key = null range scan from the lo_key
	 * to max (4) lo_key!= null, hi_key!= null, lo_key = hi_key exact match (
	 * might not unique) (5) lo_key!= null, hi_key!= null, lo_key < hi_key range
	 * scan from lo_key to hi_key
	 * 
	 * @param lo_key
	 *            the key where we begin scanning. Input parameter.
	 * @param hi_key
	 *            the key where we stop scanning. Input parameter.
	 * @exception IOException
	 *                error from the lower layer
	 * @exception KeyNotMatchException
	 *                key is not integer key nor string key
	 * @exception IteratorException
	 *                iterator error
	 * @exception ConstructPageException
	 *                error in BT page constructor
	 * @exception PinPageException
	 *                error when pin a page
	 * @exception UnpinPageException
	 *                error when unpin a page
	 */
	public BTFileScan new_scan(KeyClass lo_key, KeyClass hi_key) throws IOException, KeyNotMatchException,
			IteratorException, ConstructPageException, PinPageException, UnpinPageException

	{
		BTFileScan scan = new BTFileScan();
		if (headerPage.get_rootId().pid == INVALID_PAGE) {
			scan.leafPage = null;
			return scan;
		}

		scan.treeFilename = dbname;
		scan.endkey = hi_key;
		scan.didfirst = false;
		scan.deletedcurrent = false;
		scan.curRid = new RID();
		scan.keyType = headerPage.get_keyType();
		scan.maxKeysize = headerPage.get_maxKeySize();
		scan.bfile = this;

		// this sets up scan at the starting position, ready for iteration
		scan.leafPage = findRunStart(lo_key, scan.curRid);
		return scan;
	}

	void trace_children(PageId id)
			throws IOException, IteratorException, ConstructPageException, PinPageException, UnpinPageException {

		if (trace != null) {

			BTSortedPage sortedPage;
			RID metaRid = new RID();
			PageId childPageId;
			KeyClass key;
			KeyDataEntry entry;
			sortedPage = new BTSortedPage(pinPage(id), headerPage.get_keyType());

			// Now print all the child nodes of the page.
			if (sortedPage.getType() == NodeType.INDEX) {
				BTIndexPage indexPage = new BTIndexPage(sortedPage, headerPage.get_keyType());
				trace.writeBytes("INDEX CHILDREN " + id + " nodes" + lineSep);
				trace.writeBytes(" " + indexPage.getPrevPage());
				for (entry = indexPage.getFirst(metaRid); entry != null; entry = indexPage.getNext(metaRid)) {
					trace.writeBytes("   " + ((IndexData) entry.data).getData());
				}
			} else if (sortedPage.getType() == NodeType.LEAF) {
				BTLeafPage leafPage = new BTLeafPage(sortedPage, headerPage.get_keyType());
				trace.writeBytes("LEAF CHILDREN " + id + " nodes" + lineSep);
				for (entry = leafPage.getFirst(metaRid); entry != null; entry = leafPage.getNext(metaRid)) {
					trace.writeBytes("   " + entry.key + " " + entry.data);
				}
			}
			unpinPage(id);
			trace.writeBytes(lineSep);
			trace.flush();
		}

	}

}
