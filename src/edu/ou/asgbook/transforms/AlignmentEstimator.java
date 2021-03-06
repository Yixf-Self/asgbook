/**
 * 
 */
package edu.ou.asgbook.transforms;

import edu.ou.asgbook.core.LatLonGrid;
import edu.ou.asgbook.core.Pixel;
import edu.ou.asgbook.dataset.GlobalPopulation;
import edu.ou.asgbook.filters.SobelEdgeFilter;
import edu.ou.asgbook.transforms.FFT.Complex;

/**
 * Estimate the degree of spatial displacement between two similar grids.
 * 
 * @author v.lakshmanan
 *
 */
public class AlignmentEstimator {
	final int MAXU, MAXV;
	int motNS, motEW;
	
	/**
	 * convolve a with b and find where the maximum correlation lies
	 */
	public AlignmentEstimator(int maxu, int maxv, LatLonGrid a, LatLonGrid b){
		this.MAXU = maxu;
		this.MAXV = maxv;
		
		// a
		Complex[][] in1 = FFT2D.fft(FFT2D.zeropad(a));
		
		// zero-out an area of thickness MAXU/MAXV around the boundary to avoid boundary issues
		LatLonGrid centerb = LatLonGrid.copyOf(b);
		int minx = MAXU;
		int miny = MAXV;
		int maxx = centerb.getNumLat() - minx;
		int maxy = centerb.getNumLon() - miny;
		for (int i=0; i < b.getNumLat(); ++i){
			for (int j=0; j < b.getNumLon(); ++j){
				if (i < minx || j < miny || i > maxx || j > maxy){
					centerb.setValue(i, j, 0);
				}
			}
		}
		Complex[][] in2 = FFT2D.fft(FFT2D.zeropad(centerb));
		
		// find phase shift at this point
		for (int i=0; i < in1.length; ++i) for (int j=0; j < in1[0].length; ++j){
			in1[i][j] = in1[i][j].multiply(in2[i][j].conjugate());
			in1[i][j] = in1[i][j].multiply( 1.0 / in1[i][j].norm() );
		}
		// take ifft
		Complex[][] result = FFT2D.ifft(in1);
		
		// find location at which the convolved result is maximum
		double bestValue = Integer.MIN_VALUE;
		int startx = 0; // result.length/2 - MAXU;
		int starty = 0; // result[0].length/2 - MAXV;
		int endx = result.length; // /2 + MAXU;
		int endy = result[0].length; // /2 + MAXV;
		for (int i=startx; i < endx; ++i) for (int j=starty; j < endy; ++j){
			if ( result[i][j].normsq() > bestValue ){
				bestValue = result[i][j].real;
				motNS = -i;
				motEW = -j;
			}
		}
		
		// we don't want a 345-degree phase shift; we want it to be 15-degrees
		if ( Math.abs(motNS) > result.length/2 ){
			if (motNS < 0) motNS += result.length;
			else motNS -= result.length;
		}
		if ( Math.abs(motEW) > result[0].length/2 ){
			if (motEW < 0) motEW += result[0].length;
			else motEW -= result[0].length;
		}
	}

	public static Pixel computeCentroid(LatLonGrid a){
		double sumx = 0;
		double sumy = 0;
		double sumwt = 0;
		int N = 0;
		for (int i=0; i < a.getNumLat(); ++i) for (int j=0; j < a.getNumLon(); ++j){
			double wt = a.getValue(i,j);
			sumx += i * wt;
			sumy += j * wt;
			sumwt += wt;
			++N;
		}
		return new Pixel((int)Math.round(sumx/sumwt), (int)Math.round(sumy/sumwt), (int)Math.round(sumwt/N));
	}
	
	public static void main(String[] args) throws Exception {
		// because the alignment doesn't really check lat-lon extents,
		// cropping from offset corners will look like translation ...
		LatLonGrid conus = GlobalPopulation.read(GlobalPopulation.NORTHAMERICA);
		LatLonGrid[] grids = new LatLonGrid[2];
		grids[0] = conus.crop(900, 2500, 256, 256);
		int motx = 5; int moty = 9;
		grids[1] = conus.crop(900-motx, 2500-moty, 256, 256);
		
		// do alg
		AlignmentEstimator alg = new AlignmentEstimator(30,30,grids[0], grids[1]);
		System.out.println("Motion N/S ="  + alg.motNS + " true N/S=" + motx);
		System.out.println("Motion E/W ="  + alg.motEW + " true E/W=" + moty);
		
		System.out.println("Centroid of first = " + computeCentroid(grids[0]));
		System.out.println("Centroid of second = " + computeCentroid(grids[1]));
		
		// based on edges alone
		SobelEdgeFilter edgeFilter = new SobelEdgeFilter();
		LatLonGrid edge1 = edgeFilter.edgeFilter(grids[0]);
		LatLonGrid edge2 = edgeFilter.edgeFilter(grids[1]);
		AlignmentEstimator alg2 = new AlignmentEstimator(30,30,edge1, edge2);
		System.out.println("Edge Motion N/S ="  + alg2.motNS );
		System.out.println("Edge Motion E/W ="  + alg2.motEW );
	}
}
