package com.nyanchvig.right;

/**
 * Created by Vignesh Ravi on 28/8/2016.
 */

public class MyWord implements Comparable<MyWord>{
    public String word;
    public int occurence;

    public MyWord(String word, int occurence) {
        super();
        this.word = word;
        this.occurence = occurence;
    }

    @Override
    public int compareTo(MyWord arg0) {
        int cmp = Integer.compare(arg0.occurence,this.occurence);
        return cmp != 0 ? cmp : word.compareTo(arg0.word);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + occurence;
        result = prime * result + ((word == null) ? 0 : word.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MyWord other = (MyWord) obj;
        if (occurence != other.occurence)
            return false;
        if (word == null) {
            if (other.word != null)
                return false;
        } else if (!word.equals(other.word))
            return false;
        return true;
    }

}
