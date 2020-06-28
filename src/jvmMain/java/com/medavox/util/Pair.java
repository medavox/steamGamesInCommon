package com.medavox.util;

import java.awt.Point;

/**An unordered, commutative pair of Points. Can be compared, such that a,b == b,a*/
public class Pair
{
    private Point a;
    private Point b;
    //private boolean value;
    public Pair(Point a, Point b, boolean value) throws IllegalArgumentException
    {
        /*if(a.x < 0 || a.y < 0 || b.x < 0 || b.y < 0 )
        {
            throw new IllegalArgumentException("points must be positive");
        }
        else if(Math.abs(a.x - b.x) > 1 || Math.abs(a.y - b.y) > 1)
        {
            throw new IllegalArgumentException("points must be adjacent");
        }
        else
        {*/
            this.a = a;
            this.b = b;
            /*this.value = value;
        }*/
    }
    
    @Override
    public boolean equals(Object o)
    {
        if(!(o instanceof Pair))
        {
            return false;
        }
        else
        {
            return this.toString().equals(o.toString());
        }
    }
    public String toString()//how do you express an unordered pair in serial text?
    // guaranteed ordering!
    {//top-first, then left-first
		Point first;
		Point second;
		if(a.y == b.y)
		{//both points are at the same height; use their leftness to decide ordering instead
			if(a.x == b.x
			|| a.x < b.x)
			{//if both points are exactly the same, it doesn't matter which is printed first
				//or A is left of B, so it is printed first
				first = a;
				second = b;
			}
			else
			{//B is left of A, so B is printed first
				first = b;
				second = a;
			}
		}
		else if (a.y < b.y)
		{//point A is above B; it is printed first
			first = a;
			second = b;
		}
		else
		{//B is above A; B is printed first
			first = b;
			second = a;
		}
		return ""+first.x+","+first.y+":"+second.x+","+second.y;
    }
}
