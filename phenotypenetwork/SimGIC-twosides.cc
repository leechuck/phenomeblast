#include <string.h>
#include <stdlib.h>
#include <iostream>
#include <map>
#include <set>
#include <bitset>
#include <pthread.h>
#include <fstream>
#include <algorithm>
#include <unordered_set>
#include <unordered_map>
#include <boost/threadpool.hpp>

#define BUFFERSIZE 7000000
#define MINPHENOTYPES 7
#define THREADS 32

using namespace std;
using namespace boost::threadpool;

float ** dist;
int size;
map<string, unordered_set<string> > phenotypes ;
unordered_map<string, float > phenotypes_s ;
unordered_map<string, float > icmap;

/* non-symmetrical similarity measure; measures fraction of s1 that is contained in s2, i.e., how well does s2 covers s1; weighted by IC */
inline float simgic(unordered_set<string> s1, unordered_set<string> s2, string p1, string p2) {
  float inter = 0.0f;
  float  un = 0.0f;
  unordered_set<string>::iterator it;

  for ( it=s1.begin() ; it != s1.end(); it++ ) {
    if (s2.find(*it)!=s2.end()) {
      inter += icmap[*it];
    }
  }
  un = phenotypes_s[p1] ;

  return inter/un;
}


inline float sumset(unordered_set<string> s, unordered_map<string, float> ic) {
  unordered_set<string>::iterator it;
  float result = 0.0f;
  for (it = s.begin(); it != s.end(); it++) {
    result += ic[*it];
  }
  return result;
}

void computeline(int line) { // processes a single line of dist
  map<string, unordered_set<string> >::iterator it;
  it=phenotypes.begin();
  for ( int counter = 0 ; counter < line ; counter++) {
    it++;
  }
  string p1 = (*it).first;
  unordered_set<string> phenos = (*it).second;
  it=phenotypes.begin();
  int counter = 0 ;
  while (it != phenotypes.end()) {
    string p2 = (*it).first;
    dist[line][counter] = simgic(phenos, (*it).second, p1, p2) ;
    counter++;
    it++;
  }
  cout << "finished " << line << endl;
}

int main (int argc, char *argv[]) {
  ifstream in("../data/phenotypes.txt");
  char buffer[BUFFERSIZE];
  char * id ;
  while (in) {
    in.getline(buffer, BUFFERSIZE);
    if(in) {
      char * tok = strtok(buffer, "\t");
      unordered_set<string> mp ;
      id = tok ;
      //cout << id << endl ;
      while ((tok=strtok(NULL, "\t"))!=NULL) {
	mp.insert(tok);
      }
      int mpsize = mp.size();
      mp.reserve(mpsize);
      if (mp.size()>=MINPHENOTYPES) {
	phenotypes[id]=mp;
      }
    }
  }
  in.close();

  ifstream icin("../data/phenotypes-info.txt");
  while(icin) {
    icin.getline(buffer, BUFFERSIZE);
    if (icin) {
      char * tok = strtok(buffer, "\t");
      char * val = strtok(NULL, "\t");
      float f = atof(val);
      icmap[tok] = f;
    }
  }
  icmap.reserve(icmap.size());

  cout << "Size of icmap: " <<icmap.size() << endl;
  cout << "Size of phenotype mape: " <<phenotypes.size() << endl;
  map<string, unordered_set<string> >::iterator it1;
  for (it1 = phenotypes.begin(); it1!=phenotypes.end(); it1++) {
    phenotypes_s[(*it1).first]= sumset((*it1).second, icmap) ;
  }

  size = phenotypes.size();
  cout << "Generating matrix of size " << size << "." << endl;
  /* make result matrix */
  dist = (float**)malloc (size*sizeof(float*));
  for (int i = 0 ; i < size ; i++) {
    dist[i] = (float*)malloc(sizeof(float)*size);
  }

  cout << "Matrix generated." << endl;

  /* Make the threadpool */
  pool tp(THREADS);        /* initialise it to THREADS number of threads */

  for ( int i = 0 ; i < size ; i++) {
    //cout << i << endl;
    tp.schedule(boost::bind(&computeline,i));
  }
  tp.wait();

  ofstream fout("../data/c-all-twosided.txt");
  for (int i = 0 ; i < size ; i++) {
    for (int j = 0 ; j < size ; j++) {
      fout << dist[i][j] << "\t";
    }
    fout << "\n" ;
  }
  fout.flush();
  fout.close();
  fout.open("../data/c-all-twosided-phenotypes.txt");
 
  for (it1 = phenotypes.begin(); it1!=phenotypes.end(); it1++) {
    fout << (*it1).first << "\t" ;
  }
  fout.flush();
  fout.close();
  
  return 0;
}
