package org.joinmastodon.android.utils;

import java.util.Comparator;

public class ObjectIdComparator implements Comparator<String>{
	public static final ObjectIdComparator INSTANCE=new ObjectIdComparator();

	@Override
	public int compare(String o1, String o2){
		if(o1==null)
			return -1;
		if(o2==null)
			return 1;
		int l1=o1.length();
		int l2=o2.length();
		if(l1!=l2)
			return Integer.compare(l1, l2);
		if(l1==0)
			return 0;
		return o1.compareTo(o2);
	}
}
