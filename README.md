# binary-tree-minibase
IMPLEMENTATION DETAILS:

Delete:
Delete method will be recursively called starting with the root page until the key is found in the leaf page. Once the leaf page data is redistributed or merged, recursion will unfold, by redistributing the immediate parent page until it reaches root page or currents page satisfies the page occupancy criteria.

Data Rearrangement:
Occupancy Criteria 50%
When a key gets deleted from a leaf page occupancy of the page is checked for underflow. If the page goes underflow the data has to be either redistribute or merged.
1. Redistribute:
By finding the sibling and the parent of the underflow page we can use the existing Btree API to redistribute the data and to update the index as well. 
2. Merge:
If the sibling page which we are trying to redistribute is already at the underflow condition then redistribution will fail. And merge is the only way to redistribute the data. This happens only when two merging page data size is below the maximum page size. After merging both pages data will be put together into one of the page and the other empty page gets deleted.If the index pages are merged the parent key which is pointing the right index page will be brought down to the left index page.
