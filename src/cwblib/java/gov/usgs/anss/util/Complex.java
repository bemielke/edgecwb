/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.util;

/** This file contains an example of a Complex class, and an associated
* ComplexTest class which uses the class.
*  The Complex class doesn't run as a main program, 
*  ComplexTest is an example of a main program which uses it
*  To run the program type:
*   javac Complex.java  
*  This creates Complex.class and ComplexTest.class
*  Now type
*   java ComplexTest 

* Start by importing into the code all the classes for input and output
    * Later we'll use the class System.out.println, to print 
	* The class InputStreamReader to read
* http:*java.sun.com/j2se/1.3/docs/api/java/io/PrintStream.html
* http://java.sun.com/j2se/1.3/docs/api/java/io/InputStreamReader.html
*/
import java.io.IOException;

public class Complex {

    // Define variables for real part, imaginary part, modulus, argument etc.

    double real,imag;  // Complex Number is real + i imag
    double modulus,argument; 
    double product,creal,cimag; 
    String print;
    Complex c;
/** This is the constructor.  It initializes the x and y variables
   * @param x
   * @param y 
  * "this" has special status in java, referring to whatever object it is in.
  * If we want a complex number object, then we will write  
    * Complex cx = new Complex(8,9);
    * the program will then understand that 8 is the real part, 9 the imaginary part
 */


    public Complex(double x,  double y)
    {this.real = x;this.imag = y;} 

    // "this" - as in this.real, is used to show that we are referring
    //  to fields in the current object.  In another program, if "cx"
    //  has been defined as a complex number,  "cx.real" returns the
    //  real part (the name of the instance of Complex replaces "this")
 
/** setter method for real
   * @param realIn The real part to set
  */

  public void setReal (double realIn) {
  this.real = realIn;
  }

  /**
   * setter method for imag
   * @param imagIn
   */
  public void setImag (double imagIn) {
  this.imag = imagIn;
  }
    //  getter methods for real an imaginary
  public double getReal () { return this.real;  }
  public double getImag () { return this.imag;  }

// setter method for modulus
 void setModulus () {
  this.modulus = Math.sqrt(real*real + imag*imag);
   }

// setter method for argument
 void setArgument () {
  this.argument = Math.atan(imag/real);
   }
  public double getModulus() {return Math.sqrt(real*real+imag*imag);}
  public double getPhaseDegrees() {return Math.atan2(imag, real) * 180./Math.PI;}
    @Override
  public String toString() {return ""+real+"+I*"+imag;}
    // Multiply Complex - this is a method which will be called Complex.times
    // when used in other objects

  public static Complex times(Complex a, Complex b) {
        Complex c = new Complex(0,0);
        c.real = (a.real*b.real-a.imag*b.imag);  
        c.imag = (a.real*b.imag+a.imag*b.real);  
        return (c);
 }

    // Add Complex
 public static Complex add(Complex a, Complex b) {
        Complex c = new Complex(0,0);
        c.real = (a.real+b.real);  
        c.imag = (a.imag+b.imag);  
        return (c);
 }
    // Subtract Complex
 public static Complex subtract(Complex a, Complex b) {
        Complex c = new Complex(0,0);
        c.real = (a.real-b.real);  
        c.imag = (a.imag-b.imag);  
        return (c);
 }
  // divide complex
 public static Complex divide(Complex num, Complex denom) {
   double a=num.getReal();
   double b=num.getImag();
   double c=denom.getReal();
   double d=denom.getImag();
   return new Complex((a*c+b*d)/(c*c+d*d), (b*c-a*d)/(c*c+d*d));
 }
// setter method for writing out the complex number as a string 
void printString () {
   this.print = "{" + real + "+" + imag + "i" + "}" ;
      System.out.println(this.print);      
   }
    public static void main (String argv []) throws IOException
    {
     Complex cx = new Complex(8,9);
     // public : all other classes can call the main method
     // static : main is a class method; no instance of the class needed to 
     //          run the method
     // void : method does not return a value
     // throws : part of the exception mechanism for dealing with errors
     // IOException : ditto - we'll look at these later

      // Prompt for the 2 numbers
      // .. read in 2 values ....
        System.out.println("input real part");

        double x = 1.;

        System.out.println("input imaginary part");

        double y = 2.;

        System.out.println("The complex number you typed is");

	// define the complex number

      cx.real = x;
      cx.imag = y;

	//  Now use methods of complex class to set other things

      cx.setModulus();
      cx.setArgument();
      cx.printString();

      // Write using standard java.io class

      System.out.println("Real Part  " + cx.real);
      System.out.println("Modulus, |"+cx.real + "+" + cx.imag +"i" +"|"+ " = " + cx.modulus);
      System.out.println("Imaginary Part  " + cx.imag);      
      System.out.println("Argument  " + cx.argument);  

      // Write out using the printString method of the complex class
      // Note how cx changes its value at each step


        System.out.println("input real part of second number");

        double x1 = 3.;

        System.out.println("input imaginary part of second number");

        double y1 = 4.;

        System.out.println("The second complex number you typed is");

	// define the complex number

        Complex cy = new Complex(0,0);
      cy.real = x1;
      cy.imag = y1;

      cy.setModulus();
      cy.setArgument();
      cy.printString();

       
      System.out.println("Product of Complex numbers"); 
      Complex.times(cx,cy).printString();
      System.out.println("Sum of Complex numbers"); 
      Complex.add(cx,cy).printString();
      System.out.println("difference of Complex numbers"); 
      Complex.subtract(cx,cy).printString();
}
}
// End of the complex class


/*class ComplexTest 
{
   static BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in)); 

    public static void main (String argv []) throws IOException
    {
     Complex cx = new Complex(8,9);
     // public : all other classes can call the main method
     // static : main is a class method; no instance of the class needed to 
     //          run the method
     // void : method does not return a value
     // throws : part of the exception mechanism for dealing with errors
     // IOException : ditto - we'll look at these later

      // Prompt for the 2 numbers
      // .. read in 2 values ....
        System.out.println("input real part");

        double x = new Double(keyboard.readLine()).doubleValue();

        System.out.println("input imaginary part");

        double y = new Double(keyboard.readLine()).doubleValue();

        System.out.println("The complex number you typed is");

	// define the complex number

      cx.real = x;
      cx.imag = y;

	//  Now use methods of complex class to set other things

      cx.setModulus();
      cx.setArgument();
      cx.printString();

      // Write using standard java.io class

      System.out.println("Real Part  " + cx.real);
      System.out.println("Modulus, |"+cx.real + "+" + cx.imag +"i" +"|"+ " = " + cx.modulus);
      System.out.println("Imaginary Part  " + cx.imag);      
      System.out.println("Argument  " + cx.argument);  

      // Write out using the printString method of the complex class
      // Note how cx changes its value at each step


        System.out.println("input real part of second number");

        double x1 = new Double(keyboard.readLine()).doubleValue();

        System.out.println("input imaginary part of second number");

        double y1 = new Double(keyboard.readLine()).doubleValue();

        System.out.println("The second complex number you typed is");

	// define the complex number

        Complex cy = new Complex(0,0);
      cy.real = x1;
      cy.imag = y1;

      cy.setModulus();
      cy.setArgument();
      cy.printString();

       
      System.out.println("Product of Complex numbers"); 
      Complex.times(cx,cy).printString();
      System.out.println("Sum of Complex numbers"); 
      Complex.add(cx,cy).printString();
      System.out.println("difference of Complex numbers"); 
      Complex.subtract(cx,cy).printString();
}
}*/






